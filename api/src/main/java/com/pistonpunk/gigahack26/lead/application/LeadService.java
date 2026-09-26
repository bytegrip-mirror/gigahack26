package com.pistonpunk.gigahack26.lead.application;

import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.lead.domain.Lead;
import com.pistonpunk.gigahack26.lead.domain.LeadStatus;
import com.pistonpunk.gigahack26.lead.infrastructure.LeadRepository;
import com.pistonpunk.gigahack26.scoring.domain.PolicyScore;
import com.pistonpunk.gigahack26.scoring.infrastructure.PolicyScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeadService {

    private final LeadRepository leads;
    private final CompanyRepository companies;
    private final PolicyScoreRepository scores;
    private final Clock clock;

    public LeadService(LeadRepository leads, CompanyRepository companies, PolicyScoreRepository scores, Clock clock) {
        this.leads = leads;
        this.companies = companies;
        this.scores = scores;
        this.clock = clock;
    }

    @Transactional
    public Lead record(UUID companyId, String serviceLine, LeadStatus status, String channel, String note,
                       String outreachSubject) {
        Lead lead = leads.findByCompanyIdAndServiceLine(companyId, serviceLine)
                .orElseGet(() -> new Lead(UUID.randomUUID(), companyId, serviceLine, clock.instant()));
        lead.record(status, channel, note, outreachSubject, clock.instant());
        return leads.save(lead);
    }

    @Transactional(readOnly = true)
    public Optional<Lead> find(UUID companyId, String serviceLine) {
        return leads.findByCompanyIdAndServiceLine(companyId, serviceLine);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Lead> byCompany(String serviceLine) {
        return leads.findByServiceLine(serviceLine).stream()
                .collect(Collectors.toMap(Lead::getCompanyId, lead -> lead, (a, b) -> a, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public List<LeadView> list(String serviceLine) {
        List<Lead> found = leads.findByServiceLine(serviceLine);
        Map<UUID, Company> byId = companies.findAllById(found.stream().map(Lead::getCompanyId).toList()).stream()
                .collect(Collectors.toMap(Company::getId, company -> company));

        Map<UUID, PolicyScore> scoreByCompany = scores.findByServiceLine(serviceLine).stream()
                .collect(Collectors.toMap(PolicyScore::getCompanyId, score -> score, (a, b) -> a));

        return found.stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .map(lead -> {
                    Company company = byId.get(lead.getCompanyId());
                    PolicyScore score = scoreByCompany.get(lead.getCompanyId());
                    return new LeadView(
                            lead.getId(),
                            lead.getCompanyId(),
                            company == null ? "Unknown" : company.getName(),
                            company == null ? null : company.getDomain(),
                            lead.getServiceLine(),
                            lead.getStatus().name(),
                            lead.getStatus().label(),
                            lead.getChannel(),
                            lead.getLastContactedAt(),
                            lead.getContactCount(),
                            lead.getNote(),
                            lead.getOutreachSubject(),
                            score == null ? null : score.getProbability(),
                            score == null ? null : score.getBand().name(),
                            lead.getCrmSyncedAt(),
                            lead.getUpdatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> tally(String serviceLine) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            counts.put(status.name(), 0L);
        }
        leads.findByServiceLine(serviceLine).forEach(lead -> counts.merge(lead.getStatus().name(), 1L, Long::sum));
        counts.put("TOTAL", counts.values().stream().mapToLong(Long::longValue).sum());
        return counts;
    }

    @Transactional
    public Lead markSynced(UUID companyId, String serviceLine) {
        Lead lead = leads.findByCompanyIdAndServiceLine(companyId, serviceLine)
                .orElseGet(() -> new Lead(UUID.randomUUID(), companyId, serviceLine, clock.instant()));
        lead.markSynced(clock.instant());
        return leads.save(lead);
    }

    public boolean crmConfigured() {
        String token = System.getenv("HUBSPOT_TOKEN");
        return token != null && !token.isBlank();
    }
}
