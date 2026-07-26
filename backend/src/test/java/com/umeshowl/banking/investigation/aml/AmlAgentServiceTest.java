package com.umeshowl.banking.investigation.aml;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmlAgentServiceTest {
    @Test
    void triggersEveryAmlIndicatorAndClampsScore() {
        UUID caseId=UUID.randomUUID(), customerId=UUID.randomUUID();
        InvestigationCaseService cases=mock(InvestigationCaseService.class);
        MockTransactionRepository transactions=mock(MockTransactionRepository.class);
        AmlAgentProperties properties=new AmlAgentProperties();
        properties.setLargeTransactionThreshold(new BigDecimal("8000.00"));
        properties.setRapidCombinedThreshold(new BigDecimal("15000.00"));
        AmlAgentService service=new AmlAgentService(cases,transactions,properties);
        MockCustomer customer=new MockCustomer();
        customer.setId(customerId); customer.setRiskRating("HIGH"); customer.setPepStatus("PEP");
        customer.setAccountOpened(OffsetDateTime.now(ZoneOffset.UTC).toLocalDate());
        MockTransaction current=transaction(customer,true,"9000.00","80.00");
        current.setDestinationCountry("Iran");
        MockTransaction prior=transaction(customer,false,"9000.00","1.00");
        prior.setTransactionDate(current.getTransactionDate().minusHours(2));
        InvestigationCase investigation=new InvestigationCase();
        investigation.setId(caseId); investigation.setCustomer(customer); investigation.setTransaction(current);
        when(cases.getCase(caseId)).thenReturn(investigation);
        when(transactions.findByCustomer_IdOrderByTransactionDateDesc(customerId)).thenReturn(List.of(current,prior));

        AmlAnalysisResult result=service.analyze(caseId);

        for(AmlIndicatorType type:AmlIndicatorType.values()) {
            assertTrue(result.triggeredIndicators().stream().anyMatch(i->i.type()==type));
        }
        assertEquals(100,result.totalScore());
        assertEquals(AmlRiskLevel.CRITICAL,result.riskLevel());
    }
    @Test
    void returnsLowWhenNoTransactionIsPresent() {
        UUID caseId=UUID.randomUUID();
        InvestigationCaseService cases=mock(InvestigationCaseService.class);
        AmlAgentService service=new AmlAgentService(cases,mock(MockTransactionRepository.class),new AmlAgentProperties());
        InvestigationCase investigation=new InvestigationCase(); investigation.setId(caseId);
        when(cases.getCase(caseId)).thenReturn(investigation);
        assertEquals(AmlRiskLevel.LOW,service.analyze(caseId).riskLevel());
    }
    private MockTransaction transaction(MockCustomer customer,boolean flagged,String amount,String score) {
        MockTransaction transaction=new MockTransaction();
        transaction.setId(UUID.randomUUID()); transaction.setCustomer(customer); transaction.setFlagged(flagged);
        transaction.setAmount(new BigDecimal(amount)); transaction.setRiskScore(new BigDecimal(score));
        transaction.setTransactionDate(OffsetDateTime.now(ZoneOffset.UTC)); transaction.setOriginCountry("United States"); transaction.setDestinationCountry("United States");
        return transaction;
    }
}
