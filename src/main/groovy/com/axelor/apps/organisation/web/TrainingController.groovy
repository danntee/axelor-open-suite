package com.axelor.apps.organisation.web

import org.joda.time.Days
import org.joda.time.LocalDate
import groovy.util.logging.Slf4j
import com.axelor.rpc.ActionRequest
import com.axelor.rpc.ActionResponse
import com.google.inject.Inject
import com.axelor.apps.organisation.db.Training

@Slf4j
class TrainingController {
	def computeDuration(ActionRequest request, ActionResponse response) {
		
		Training training = request.context as Training
		
		if (training.endDate && training.startDate) {
			Days d = Days.daysBetween(training.startDate, training.endDate)
			response.values = [ "duration" : d.getDays()]
		}
		else {
			response.values = [ "duration" : null]
		}
	}
	
	def computeDate(ActionRequest request, ActionResponse response) {
		
		Training training = request.context as Training
		int duration = training.duration.toInteger()
		
		if (duration >= 0) {
			if (training.startDate) {
				response.values = [ "endDate" : training.startDate.plusDays(duration) ]
			}
			else if (training.endDate) {
				response.values = [ "startDate" : training.endDate.minusDays(duration) ]
			}
		}
	}
}
