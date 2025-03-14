/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.sale.service.saleorderline.tax;

import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.CurrencyScaleService;
import com.axelor.apps.base.service.tax.OrderLineTaxService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.SaleOrderLineTax;
import com.axelor.common.ObjectUtils;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderLineCreateTaxLineServiceImpl implements SaleOrderLineCreateTaxLineService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected OrderLineTaxService orderLineTaxService;
  protected CurrencyScaleService currencyScaleService;

  @Inject
  public SaleOrderLineCreateTaxLineServiceImpl(
      OrderLineTaxService orderLineTaxService, CurrencyScaleService currencyScaleService) {
    this.orderLineTaxService = orderLineTaxService;
    this.currencyScaleService = currencyScaleService;
  }

  /**
   * Créer les lignes de TVA du devis. La création des lignes de TVA se basent sur les lignes de
   * devis ainsi que les sous-lignes de devis de celles-ci. Si une ligne de devis comporte des
   * sous-lignes de devis, alors on se base uniquement sur celles-ci.
   *
   * @param saleOrder Le devis de vente.
   * @param saleOrderLineList Les lignes du devis de vente.
   * @return La liste des lignes de taxe du devis de vente.
   */
  @Override
  public List<SaleOrderLineTax> createsSaleOrderLineTax(
      SaleOrder saleOrder, List<SaleOrderLine> saleOrderLineList) {

    List<SaleOrderLineTax> saleOrderLineTaxList = new ArrayList<>();
    List<SaleOrderLineTax> currentSaleOrderLineTaxList = new ArrayList<>();
    currentSaleOrderLineTaxList.addAll(saleOrder.getSaleOrderLineTaxList());
    saleOrder.clearSaleOrderLineTaxList();

    Map<TaxLine, SaleOrderLineTax> map = new HashMap<>();
    Set<String> specificNotes = new HashSet<>();
    boolean customerSpecificNote = orderLineTaxService.isCustomerSpecificNote(saleOrder);

    if (CollectionUtils.isNotEmpty(saleOrderLineList)) {
      LOG.debug("Creation of VAT lines for sale order lines.");
      for (SaleOrderLine saleOrderLine : saleOrderLineList) {
        getOrCreateLines(saleOrder, saleOrderLine, map, customerSpecificNote, specificNotes);
      }
    }

    computeAndAddTaxToList(
        map, saleOrderLineTaxList, saleOrder.getCurrency(), currentSaleOrderLineTaxList);
    orderLineTaxService.setSpecificNotes(
        customerSpecificNote,
        saleOrder,
        specificNotes,
        saleOrder.getClientPartner().getSpecificTaxNote());

    return saleOrderLineTaxList;
  }

  protected void getOrCreateLines(
      SaleOrder saleOrder,
      SaleOrderLine saleOrderLine,
      Map<TaxLine, SaleOrderLineTax> map,
      boolean customerSpecificNote,
      Set<String> specificNotes) {
    Set<TaxLine> taxLineSet = saleOrderLine.getTaxLineSet();
    if (CollectionUtils.isNotEmpty(taxLineSet)) {
      for (TaxLine taxLine : taxLineSet) {
        getOrCreateLine(saleOrder, saleOrderLine, map, taxLine);
      }
    }
    orderLineTaxService.addTaxEquivSpecificNote(saleOrderLine, customerSpecificNote, specificNotes);
  }

  protected void getOrCreateLine(
      SaleOrder saleOrder,
      SaleOrderLine saleOrderLine,
      Map<TaxLine, SaleOrderLineTax> map,
      TaxLine taxLine) {
    if (taxLine != null) {
      LOG.debug("Tax {}", taxLine);
      if (map.containsKey(taxLine)) {
        SaleOrderLineTax saleOrderLineTax = map.get(taxLine);
        saleOrderLineTax.setExTaxBase(
            currencyScaleService.getScaledValue(
                saleOrder, saleOrderLineTax.getExTaxBase().add(saleOrderLine.getExTaxTotal())));
      } else {
        SaleOrderLineTax saleOrderLineTax =
            createSaleOrderLineTax(saleOrder, saleOrderLine, taxLine);
        map.put(taxLine, saleOrderLineTax);
      }
    }
  }

  protected SaleOrderLineTax createSaleOrderLineTax(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) {
    SaleOrderLineTax saleOrderLineTax = new SaleOrderLineTax();
    saleOrderLineTax.setSaleOrder(saleOrder);
    saleOrderLineTax.setExTaxBase(saleOrderLine.getExTaxTotal());
    saleOrderLineTax.setTaxLine(taxLine);
    saleOrderLineTax.setTaxType(
        Optional.ofNullable(taxLine.getTax()).map(Tax::getTaxType).orElse(null));
    return saleOrderLineTax;
  }

  protected void computeAndAddTaxToList(
      Map<TaxLine, SaleOrderLineTax> map,
      List<SaleOrderLineTax> saleOrderLineTaxList,
      Currency currency,
      List<SaleOrderLineTax> currentSaleOrderLineTaxList) {
    for (SaleOrderLineTax saleOrderLineTax : map.values()) {
      // Dans la devise de la facture
      orderLineTaxService.computeTax(saleOrderLineTax, currency);
      SaleOrderLineTax oldSaleOrderLineTax =
          getExistingSaleOrderLineTax(saleOrderLineTax, currentSaleOrderLineTaxList);
      if (oldSaleOrderLineTax == null) {
        saleOrderLineTaxList.add(saleOrderLineTax);
        LOG.debug(
            "VAT line : VAT total => {}, W.T. total => {}",
            saleOrderLineTax.getTaxTotal(),
            saleOrderLineTax.getInTaxTotal());
      } else {
        saleOrderLineTaxList.add(oldSaleOrderLineTax);
      }
    }
  }

  @Override
  public List<SaleOrderLineTax> getUpdatedSaleOrderLineTax(SaleOrder saleOrder) {
    List<SaleOrderLineTax> saleOrderLineTaxList = new ArrayList<>();

    if (ObjectUtils.isEmpty(saleOrder.getSaleOrderLineTaxList())) {
      return saleOrderLineTaxList;
    }

    saleOrderLineTaxList.addAll(
        saleOrder.getSaleOrderLineTaxList().stream()
            .filter(
                saleOrderLineTax ->
                    orderLineTaxService.isManageByAmount(saleOrderLineTax)
                        && saleOrderLineTax
                                .getTaxTotal()
                                .compareTo(saleOrderLineTax.getPercentageTaxTotal())
                            != 0)
            .collect(Collectors.toList()));
    return saleOrderLineTaxList;
  }

  protected SaleOrderLineTax getExistingSaleOrderLineTax(
      SaleOrderLineTax saleOrderLineTax, List<SaleOrderLineTax> saleOrderLineTaxList) {
    if (ObjectUtils.isEmpty(saleOrderLineTaxList) || saleOrderLineTax == null) {
      return null;
    }

    for (SaleOrderLineTax saleOrderLineTaxItem : saleOrderLineTaxList) {
      if (Objects.equals(saleOrderLineTaxItem.getTaxLine(), saleOrderLineTax.getTaxLine())
          && saleOrderLineTaxItem.getPercentageTaxTotal().compareTo(saleOrderLineTax.getTaxTotal())
              == 0
          && saleOrderLineTaxItem.getExTaxBase().compareTo(saleOrderLineTax.getExTaxBase()) == 0) {
        return saleOrderLineTaxItem;
      }
    }

    return null;
  }
}
