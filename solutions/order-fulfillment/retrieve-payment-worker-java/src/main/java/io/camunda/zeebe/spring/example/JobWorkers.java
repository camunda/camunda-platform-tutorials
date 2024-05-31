package io.camunda.zeebe.spring.example;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;

import java.time.Instant;

import io.camunda.zeebe.spring.client.annotation.VariablesAsType;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobWorkers {

  private static Logger log = LoggerFactory.getLogger(JobWorkers.class);

  @JobWorker()
  public Order retrievePayment(final ActivatedJob job, @VariablesAsType Order variables) {
    logJob(job, variables.toString());

    if(variables.getPaymentShouldFail() == null || variables.getPaymentShouldFail() == false){
      variables.setOrderAmount(variables.getArticlePrice()*variables.getArticleQuantity());
      variables.setPaymentRetrieved(true);
      return variables;
    }

    else {
      throw new ZeebeBpmnError("paymentFailed", "The payment failed due to an error");
    }

  }

  // A (disabled) job worker for the second task of the order fulfillment process ("Fetch goods")

  @JobWorker(enabled = false)
  public Order fetchGoods(final ActivatedJob job, @VariablesAsType Order variables) {
    logJob(job, variables.toString());
    variables.setGoodsFetched(true);
    return variables;
  }

  // A (disabled) job worker for the second task of the order fulfillment process ("Ship goods")

  @JobWorker(enabled = false)
  public Order shipGoods(final ActivatedJob job, @VariablesAsType Order variables) {
    logJob(job, variables.toString());
    variables.setGoodsShipped(true);
    return variables;
  }


  private static void logJob(final ActivatedJob job, Object parameterValue) {
    log.info(
      "complete job\n>>> [type: {}, key: {}, element: {}, workflow instance: {}]\n{deadline; {}]\n[headers: {}]\n[variable parameter: {}\n[variables: {}]",
      job.getType(),
      job.getKey(),
      job.getElementId(),
      job.getProcessInstanceKey(),
      Instant.ofEpochMilli(job.getDeadline()),
      job.getCustomHeaders(),
      parameterValue,
      job.getVariables());
  }

}
