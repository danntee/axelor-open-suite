package com.axelor.apps.base.openapi;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.common.StringUtils;
import com.axelor.i18n.I18n;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AosSwagger {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public OpenAPI initSwagger() {
    OpenAPI openAPI = null;
    SwaggerConfiguration swaggerConfiguration = getSwaggerConfiguration(AppSettings.get());

    try {
      openAPI =
          new JaxrsOpenApiContextBuilder()
              .openApiConfiguration(swaggerConfiguration)
              .buildContext(true)
              .read();
    } catch (OpenApiConfigurationException e) {
      LOG.error("{}", e);
    }
    return openAPI;
  }

  protected SwaggerConfiguration getSwaggerConfiguration(AppSettings appSettings) {

    Set<String> resourcePackages = getResourcePackagesForSwaggerConfig(appSettings);
    if (resourcePackages.isEmpty()) {
      return null;
    }
    OpenAPI oas = new OpenAPI();
    oas.info(getInfo(appSettings));

    return new SwaggerConfiguration()
        .openAPI(oas)
        .resourcePackages(resourcePackages)
        .readAllResources(false);
  }

  protected Info getInfo(AppSettings appSettings) {
    return new Info()
        .title(appSettings.get("application.name"))
        .description(appSettings.get("application.description"))
        .version(appSettings.get("application.version"));
  }

  protected Set<String> getResourcePackagesForSwaggerConfig(AppSettings appSettings) {
    String resourcePackages = appSettings.get("aos.swagger.resource-packages");
    if (resourcePackages == null || StringUtils.isBlank(resourcePackages)) {
      LOG.info(I18n.get(BaseExceptionMessage.SWAGGER_NO_RESOURCE_PACKAGES));
      return Collections.emptySet();
    }
    resourcePackages = resourcePackages.replaceAll("\\s", "");
    String[] packagesList = resourcePackages.split(",");

    return Set.of(packagesList);
  }
}
