package com.pistonpunk.gigahack26.company.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "company_alias")
public class CompanyAlias {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 200)
    private String alias;

    protected CompanyAlias() {
    }

    public CompanyAlias(UUID id, UUID companyId, String alias) {
        this.id = id;
        this.companyId = companyId;
        this.alias = alias;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getAlias() {
        return alias;
    }
}
