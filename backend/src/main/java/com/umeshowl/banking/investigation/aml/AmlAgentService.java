package com.umeshowl.banking.investigation.aml;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AmlAgentService {
    private final InvestigationCaseService caseService;
    private final MockTransactionRepository transactionRepository;
    private final AmlAgentProperties properties;
    public AmlAgentService(InvestigationCaseService caseService, MockTransactionRepository transactionRepository, AmlAgentProperties properties) {
        this.caseService=caseService; this.transactionRepository=transactionRepository; this.properties=properties;
    }
    @Transactional(readOnly = true)
    public AmlAnalysisResult analyze(UUID investigationId) {
        InvestigationCase investigation = caseService.getCase(investigationId);
        MockTransaction transaction=investigation.getTransaction();
        MockCustomer customer=investigation.getCustomer();
        if (customer==null && transaction!=null) customer=transaction.getCustomer();
        List<AmlIndicator> indicators=new ArrayList<>();
        if (transaction!=null) evaluate(transaction, customer, history(customer, transaction), indicators);
        int score=Math.clamp(indicators.stream().mapToInt(AmlIndicator::scoreContribution).sum(),0,100);
        AmlRiskLevel level=level(score);
        return new AmlAnalysisResult(investigation.getId(), customer==null?null:customer.getId(), transaction==null?null:transaction.getId(), score, level,
                indicators.isEmpty()?"No deterministic AML indicators were triggered.":indicators.size()+" deterministic AML indicators triggered; score "+score+" ("+level+").",
                indicators, OffsetDateTime.now(ZoneOffset.UTC));
    }
    private List<MockTransaction> history(MockCustomer customer, MockTransaction transaction) {
        if(customer==null||customer.getId()==null) return List.of(transaction);
        List<MockTransaction> items=new ArrayList<>(transactionRepository.findByCustomer_IdOrderByTransactionDateDesc(customer.getId()));
        if(items.stream().noneMatch(item->transaction.getId()!=null&&transaction.getId().equals(item.getId()))) items.add(transaction);
        return items;
    }
    private void evaluate(MockTransaction tx, MockCustomer customer, List<MockTransaction> history, List<AmlIndicator> out) {
        if(tx.isFlagged()) add(out,AmlIndicatorType.FLAGGED_TRANSACTION,properties.getFlaggedTransactionScore(),"Transaction is flagged",Map.of("flagged",true));
        if(atLeast(tx.getRiskScore(),properties.getHighRiskScoreThreshold())) add(out,AmlIndicatorType.HIGH_TRANSACTION_RISK_SCORE,properties.getHighTransactionRiskScore(),"Transaction risk score exceeds configured AML threshold",Map.of("riskScore",tx.getRiskScore(),"threshold",properties.getHighRiskScoreThreshold()));
        if(atLeast(tx.getAmount(),properties.getLargeTransactionThreshold())) add(out,AmlIndicatorType.LARGE_TRANSACTION,properties.getLargeTransactionScore(),"Transaction amount exceeds configured AML threshold",Map.of("amount",tx.getAmount(),"threshold",properties.getLargeTransactionThreshold()));
        if(highCountry(tx.getOriginCountry())||highCountry(tx.getDestinationCountry())) add(out,AmlIndicatorType.HIGH_RISK_COUNTRY,properties.getHighRiskCountryScore(),"Transaction involves a configured high-risk country",Map.of("origin",safe(tx.getOriginCountry()),"destination",safe(tx.getDestinationCountry())));
        if(customer!=null&&"HIGH".equalsIgnoreCase(customer.getRiskRating())) add(out,AmlIndicatorType.HIGH_RISK_CUSTOMER,properties.getHighRiskCustomerScore(),"Customer risk rating is HIGH",Map.of("riskRating",customer.getRiskRating()));
        if(customer!=null&&"PEP".equalsIgnoreCase(customer.getPepStatus())&&atLeast(tx.getAmount(),properties.getLargeTransactionThreshold())) add(out,AmlIndicatorType.PEP_ACTIVITY,properties.getPepActivityScore(),"PEP customer has high-value transaction activity",Map.of("amount",tx.getAmount()));
        List<MockTransaction> rapid=window(history,tx.getTransactionDate(),properties.getRapidWindowHours());
        if(rapid.size()>=2&&total(rapid).compareTo(properties.getRapidCombinedThreshold())>0) add(out,AmlIndicatorType.RAPID_MOVEMENT,properties.getRapidMovementScore(),"Multiple transactions exceed the rapid movement threshold",Map.of("combinedAmount",total(rapid),"threshold",properties.getRapidCombinedThreshold()));
        List<MockTransaction> structured=window(history,tx.getTransactionDate(),properties.getRapidWindowHours()*7).stream().filter(item->item.getAmount()!=null&&item.getAmount().compareTo(properties.getReportingThreshold())<0).toList();
        if(structured.size()>=2&&total(structured).compareTo(properties.getReportingThreshold())>0) add(out,AmlIndicatorType.STRUCTURING,properties.getStructuringScore(),"Below-reporting-threshold transactions exceed reporting threshold in aggregate",Map.of("combinedAmount",total(structured),"threshold",properties.getReportingThreshold()));
        if(customer!=null&&newAccount(customer,tx)&&(tx.isFlagged()||atLeast(tx.getAmount(),properties.getLargeTransactionThreshold()))) add(out,AmlIndicatorType.NEW_ACCOUNT_ACTIVITY,properties.getNewAccountActivityScore(),"New account has unusually large or flagged transaction activity",Map.of("accountOpened",customer.getAccountOpened()));
        if(out.size()>=3) add(out,AmlIndicatorType.MULTIPLE_HIGH_RISK_INDICATORS,properties.getMultipleHighRiskIndicatorsScore(),"Several independent AML indicators occurred together",Map.of("indicatorCount",out.size()));
    }
    private List<MockTransaction> window(List<MockTransaction> items, OffsetDateTime reference, int hours) { if(reference==null)return List.of(); OffsetDateTime start=reference.minusHours(hours); return items.stream().filter(i->i.getTransactionDate()!=null&&!i.getTransactionDate().isBefore(start)&&!i.getTransactionDate().isAfter(reference)).toList(); }
    private BigDecimal total(List<MockTransaction> items){return items.stream().map(MockTransaction::getAmount).filter(a->a!=null).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private boolean highCountry(String country){return country!=null&&properties.getHighRiskCountries().stream().map(this::normalize).anyMatch(normalize(country)::equals);}
    private boolean newAccount(MockCustomer customer,MockTransaction tx){return customer.getAccountOpened()!=null&&tx.getTransactionDate()!=null&&!customer.getAccountOpened().plusDays(properties.getNewAccountDays()).isBefore(tx.getTransactionDate().toLocalDate());}
    private boolean atLeast(BigDecimal value,BigDecimal threshold){return value!=null&&threshold!=null&&value.compareTo(threshold)>=0;}
    private AmlRiskLevel level(int score){if(score>=80)return AmlRiskLevel.CRITICAL;if(score>=60)return AmlRiskLevel.HIGH;if(score>=30)return AmlRiskLevel.MEDIUM;return AmlRiskLevel.LOW;}
    private void add(List<AmlIndicator> out,AmlIndicatorType type,int score,String explanation,Map<String,Object> evidence){out.add(new AmlIndicator(type,score,explanation,evidence));}
    private String normalize(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
    private String safe(String value){return value==null?"":value;}
}
