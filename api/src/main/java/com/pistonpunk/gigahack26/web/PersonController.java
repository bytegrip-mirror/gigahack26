package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Person;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.service.BoardService;
import com.pistonpunk.gigahack26.service.PersonService;
import com.pistonpunk.gigahack26.web.dto.BoardRow;
import com.pistonpunk.gigahack26.web.dto.PersonRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final BoardService board;
    private final CompanyRepository companies;

    public PersonController(PersonService people, BoardService board, CompanyRepository companies) {
        this.people = people;
        this.board = board;
        this.companies = companies;
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
                    row.profiles()));
        }
        return out;
    }

    @PostMapping("/extract")
    public Map<String, Object> extract() {
        int added = people.extract(companies.findAll());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("added", added);
        body.put("total", people.forCompanies(companies.findAll().stream().map(Company::getId).toList()).size());
        return body;
    }
}
