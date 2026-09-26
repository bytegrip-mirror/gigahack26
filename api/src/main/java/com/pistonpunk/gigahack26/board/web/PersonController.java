package com.pistonpunk.gigahack26.board.web;

import com.pistonpunk.gigahack26.board.application.BoardRow;
import com.pistonpunk.gigahack26.board.application.BoardService;
import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.person.application.PersonSeeder;
import com.pistonpunk.gigahack26.person.application.PersonService;
import com.pistonpunk.gigahack26.person.domain.Person;
import com.pistonpunk.gigahack26.person.domain.PersonSignal;
import com.pistonpunk.gigahack26.person.infrastructure.EnrichmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService people;
    private final PersonSeeder seeder;
    private final BoardService board;
    private final CompanyRepository companies;
    private final EnrichmentService enrichment;

    public PersonController(PersonService people, PersonSeeder seeder, BoardService board,
                            CompanyRepository companies, EnrichmentService enrichment) {
        this.people = people;
        this.seeder = seeder;
        this.board = board;
        this.companies = companies;
        this.enrichment = enrichment;
    }

    private PersonRow row(Person person) {
        return new PersonRow(person.getId(), person.getCompanyId(), null, null, person.getName(), person.getRole(),
                null, 0, null, person.getSourceUrl(), person.getPublisher(), person.getPublishedAt(),
                person.getEmail(), person.getLinkedinUrl(), person.getHeadline(), person.getEnrichedAt(),
                person.getStatus().name(), person.getStatus().label(), person.getContactCount(),
                person.getLastContactedAt(), person.getOutreachSubject(), List.of(), List.of());
    }

    @GetMapping("/provider")
    public Map<String, Object> provider() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", enrichment.provider());
        body.put("available", enrichment.available());
        return body;
    }

    @GetMapping
    public List<PersonRow> list(@RequestParam(name = "serviceLine") String serviceLine) {
        List<BoardRow> rows = board.board(serviceLine, "all", "score", null, null, null);
        Map<UUID, BoardRow> byCompany = new LinkedHashMap<>();
        for (BoardRow row : rows) {
            byCompany.put(row.id(), row);
        }
        List<Person> found = people.forCompanies(new ArrayList<>(byCompany.keySet()));

        Map<UUID, String> names = people.companyNames();
        Map<UUID, String> domains = new LinkedHashMap<>();
        for (Company company : companies.findAll()) {
            domains.put(company.getId(), company.getDomain());
        }

        Map<UUID, List<PersonSignal>> said = people.signalsFor(found.stream().map(Person::getId).toList());

        List<PersonRow> out = new ArrayList<>();
        for (Person person : found) {
            BoardRow row = byCompany.get(person.getCompanyId());
            if (row == null) {
                continue;
            }
            out.add(new PersonRow(
                    person.getId(),
                    person.getCompanyId(),
                    names.getOrDefault(person.getCompanyId(), row.name()),
                    domains.get(person.getCompanyId()),
                    person.getName(),
                    person.getRole(),
                    row.band(),
                    row.probability(),
                    serviceLine,
                    person.getSourceUrl(),
                    person.getPublisher(),
                    person.getPublishedAt(),
                    person.getEmail(),
                    person.getLinkedinUrl(),
                    person.getHeadline(),
                    person.getEnrichedAt(),
                    person.getStatus().name(),
                    person.getStatus().label(),
                    person.getContactCount(),
                    person.getLastContactedAt(),
                    person.getOutreachSubject(),
                    row.profiles(),
                    said.getOrDefault(person.getId(), List.of()).stream().map(PersonController::signalRow).toList()));
        }
        return out;
    }

    private static PersonSignalRow signalRow(PersonSignal signal) {
        return new PersonSignalRow(signal.getId(), signal.getKind(), signal.getStrength(), signal.getText(),
                signal.getSourceUrl(), signal.getPublisher(), signal.getPublishedAt());
    }

    public record StageRequest(String status, String channel, String subject) {
    }

    public record EmailRequest(String email) {
    }

    @PostMapping("/{id}/email")
    public ResponseEntity<PersonRow> email(@PathVariable UUID id, @RequestBody EmailRequest request) {
        return people.setEmail(id, request.email())
                .map(person -> ResponseEntity.ok(row(person)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/stage")
    public ResponseEntity<PersonRow> stage(@PathVariable UUID id, @RequestBody StageRequest request) {
        return people.move(id, request.status(), request.channel(), request.subject())
                .map(person -> ResponseEntity.ok(row(person)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/enrich")
    public ResponseEntity<PersonRow> enrichOne(@PathVariable UUID id) {
        return people.enrich(id, enrichment)
                .map(person -> ResponseEntity.ok(row(person)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/enrich")
    public Map<String, Object> enrichMany(@RequestParam(name = "serviceLine") String serviceLine,
                                          @RequestParam(name = "limit", defaultValue = "25") int limit) {
        return people.enrichAll(serviceLine, Math.max(1, Math.min(200, limit)), enrichment);
    }

    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestParam(name = "reset", defaultValue = "false") boolean reset) {
        List<Company> all = companies.findAll();
        if (reset) {
            people.forget();
        }
        int added = people.extract(all);
        int seeded = reset ? seeder.seed() : 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("added", added);
        body.put("seeded", seeded);
        body.put("reset", reset);
        body.put("total", people.forCompanies(all.stream().map(Company::getId).toList()).size());
        return body;
    }
}
