package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.DecayClass;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.WeightTier;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PolicyLibrary {

    public static final String AUTOMATION = "Intelligent Automation";
    public static final String SECURITY = "Cybersecurity";
    public static final String DATA = "Data & AI";

    private final PolicyQuestionRepository questions;
    private final Clock clock;

    public PolicyLibrary(PolicyQuestionRepository questions, Clock clock) {
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional
    public void ensureSeeded() {
        for (Definition definition : definitions()) {
            Optional<PolicyQuestion> existing = questions.findByKey(definition.key());
            if (existing.isEmpty()) {
                questions.save(toEntity(definition));
                continue;
            }
            PolicyQuestion question = existing.get();
            question.revise(
                    definition.text(),
                    definition.instructions(),
                    definition.tier(),
                    definition.polarity(),
                    definition.theme(),
                    definition.decayClass(),
                    definition.cues(),
                    definition.counterCues(),
                    question.isActive(),
                    clock.instant());
            questions.save(question);
        }
    }

    public record Definition(String key, String serviceLine, String text, String instructions,
                             WeightTier tier, Polarity polarity, String theme, DecayClass decayClass,
                             List<String> cues, List<String> counterCues) {
    }

    private PolicyQuestion toEntity(Definition definition) {
        return new PolicyQuestion(
                UUID.randomUUID(),
                definition.key(),
                definition.serviceLine(),
                definition.text(),
                definition.instructions(),
                definition.tier(),
                definition.polarity(),
                definition.theme(),
                definition.decayClass(),
                definition.cues(),
                definition.counterCues(),
                true,
                clock.instant());
    }

    public static List<Definition> definitions() {
        return List.of(
                new Definition(
                        "automation.programme", AUTOMATION,
                        "Has the company announced an efficiency, cost-reduction or process-optimisation programme with a quantified target?",
                        """
                        Counts: a named programme, a stated financial or headcount target, or a public commitment to \
                        reduce cost per transaction, consolidate shared services, or remove manual processing.

                        Does not count: a generic statement that the company \"invests in digital\", a product page, \
                        or a supplier's description of work it did for someone else. The programme must belong to this \
                        company and must be current.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "efficiency", DecayClass.STRUCTURAL,
                        List.of("cost reduction", "efficiency programme", "efficiency target", "operating efficiency",
                                "process optimisation", "process optimization", "operational excellence",
                                "shared services", "process consolidation", "cost savings", "savings target",
                                "productivity programme", "headcount reduction", "reduce costs", "streamline operations",
                                "process efficiency", "automation programme", "digital transformation programme",
                                "job cuts", "cut jobs", "cutting jobs", "reduce headcount", "headcount cut",
                                "restructuring", "restructure", "cost cutting", "cost-cutting", "efficiency drive",
                                "productivity gains", "operating margin", "savings programme", "spending cuts",
                                "simplify operations", "operational efficiency programme", "reduce operating costs"),
                        List.of("no plans to", "already completed")),
                new Definition(
                        "automation.hiring", AUTOMATION,
                        "Is the company hiring for automation, RPA, process-mining or process-excellence roles that select, govern or manage automation rather than build it?",
                        """
                        Counts: roles whose job is to choose, govern, roll out or manage automation across the business \
                        — process excellence, process mining, automation programme lead, RPA business analyst, centre \
                        of excellence roles. Hiring for high-volume manual processing work also counts, because that \
                        is the work automation replaces.

                        Does not count: a role that writes automation code as a product for external customers, or an \
                        internship. A vendor hiring RPA engineers is building, not buying.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "capability", DecayClass.HIRING,
                        List.of("rpa developer", "rpa engineer", "automation engineer", "automation developer",
                                "process mining", "process excellence", "robotic process automation",
                                "intelligent automation", "automation analyst", "process automation",
                                "business process analyst", "blue prism", "uipath", "power automate",
                                "automation specialist", "automation consultant", "workflow automation",
                                "process improvement", "lean six sigma", "operations excellence",
                                "digital transformation", "business process", "process lead", "transformation office",
                                "change management", "continuous improvement", "operational excellence manager",
                                "automation lead", "head of automation", "shared services"),
                        List.of("internship", "intern,", "unpaid", "student worker")),
                new Definition(
                        "automation.insourcing", AUTOMATION,
                        "Is the company building automation capability in-house — an internal automation centre of excellence, an in-house platform, or its own automation product?",
                        """
                        Counts: the company states it develops automation itself, runs an internal automation factory \
                        or centre of excellence, or sells an automation product of its own. This is a company that \
                        will build rather than buy, so it is removed from the queue and the reason is recorded.

                        Does not count: a company that has an internal team overseeing external delivery partners.""",
                        WeightTier.HIGH, Polarity.DISQUALIFYING, "in-house", DecayClass.STRUCTURAL,
                        List.of("centre of excellence", "center of excellence", "in-house automation",
                                "internal automation team", "automation factory", "in-house developed",
                                "own automation platform", "proprietary automation", "internal r&d",
                                "we build our own", "developed in-house", "own rpa"),
                        List.of("delivery partner", "systems integrator", "third-party provider", "outsourced")),
                new Definition(
                        "automation.vendor_lock", AUTOMATION,
                        "Does the company already run a multi-year automation programme with a named vendor, which would make switching unlikely?",
                        """
                        Counts: a signed multi-year agreement, a named strategic partner for automation, or a \
                        contract renewal or extension with a specific supplier.

                        This is counter-evidence, not a disqualifier: a company with an incumbent partner is harder \
                        to sell to, but the research says prior vendor experience is also the strongest predictor of \
                        buying again, so it reduces the score rather than removing the company.""",
                        WeightTier.MEDIUM, Polarity.NEGATIVE, "capability", DecayClass.STRUCTURAL,
                        List.of("multi-year agreement", "strategic partnership with", "extended its partnership",
                                "signed a contract with", "selected as its", "renewed its contract",
                                "named as the preferred"),
                        List.of("evaluating", "request for proposal", "tender", "proof of concept")),
                new Definition(
                        "security.leadership", SECURITY,
                        "Has the company appointed a new CISO, head of security or equivalent security leader?",
                        """
                        Counts: a named appointment or promotion into a role that owns security for the whole \
                        organisation, announced publicly.

                        Does not count: a security engineer hire, a vendor's own staff, or a role below director \
                        level. A new security leader reviews the inherited stack, which is a buying moment.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "capability", DecayClass.HIRING,
                        List.of("chief information security officer", "ciso", "head of cybersecurity",
                                "head of security", "director of information security", "vp of security",
                                "chief security officer", "appointed chief information security",
                                "group security officer", "security leadership", "chief security officer",
                                "new ciso", "appointed ciso", "head of cyber", "security chief",
                                "information security officer", "cyber security lead"),
                        List.of("internship", "advisory board")),
                new Definition(
                        "security.compliance", SECURITY,
                        "Is the company subject to a named regulatory obligation or deadline it must meet — NIS2, DORA, ISO 27001, PCI DSS?",
                        """
                        Counts: a named regulation or framework with a deadline, an audit requirement, or a \
                        certification programme the company says it is working towards.

                        Does not count: a vendor explaining what the regulation means in general, or an article \
                        about the regulation with no connection to this company's own obligations.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "compliance", DecayClass.STRUCTURAL,
                        List.of("nis2", "dora", "iso 27001", "soc 2", "pci dss", "psd2", "cyber resilience",
                                "regulatory compliance", "compliance deadline", "compliance requirements",
                                "audit requirement", "certification programme", "regulatory obligation",
                                "must comply", "regulatory pressure", "compliance burden", "cyber security directive",
                                "security regulation", "data protection regulation", "compliance framework",
                                "cyber resilience act", "security certification", "iso/iec 27001"),
                        List.of("what is nis2", "explainer", "guide to")),
                new Definition(
                        "security.incident", SECURITY,
                        "Has the company suffered a breach, attack or security enforcement action?",
                        """
                        Counts: a reported breach, ransomware incident, data leak, regulatory fine or enforcement \
                        action that names this company.

                        Does not count: industry-wide statistics, a same-named company, or a breach at a supplier \
                        reported as a general news item.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "incident", DecayClass.EVENT,
                        List.of("data breach", "cyber attack", "cyberattack", "ransomware", "security incident",
                                "exposed data", "data leak", "hacked", "fined by", "enforcement action",
                                "suffered a breach", "security flaw", "vulnerability exposed",
                                "cyber incident", "cybersecurity incident", "data breach", "security breach",
                                "attacked by", "extortion", "stolen data", "compromised", "zero-day",
                                "vulnerability", "patch", "security failure", "regulator fined"),
                        List.of("how to protect", "avoid becoming")),
                new Definition(
                        "security.insourcing", SECURITY,
                        "Is the company building its own security operations capability in-house?",
                        """
                        Counts: the company says it runs or is building its own security operations centre, or an \
                        internal security engineering function that replaces external providers.

                        Does not count: a company that outsources to a managed security provider.""",
                        WeightTier.HIGH, Polarity.DISQUALIFYING, "in-house", DecayClass.STRUCTURAL,
                        List.of("security operations centre", "security operations center", "in-house soc",
                                "own soc", "in-house security team", "security centre of excellence",
                                "internal security operations", "we handle security ourselves"),
                        List.of("managed security provider", "outsourced security")),
                new Definition(
                        "data.initiative", DATA,
                        "Has the company started a data, analytics or AI initiative with a named use case?",
                        """
                        Counts: a named AI or data programme, a named use case in production or pilot, or a stated \
                        ambition with a business owner attached.

                        Does not count: a vendor case study about someone else, a generic \"we are exploring AI\" \
                        statement with no use case, or a prediction about the industry.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "initiative", DecayClass.STRUCTURAL,
                        List.of("artificial intelligence strategy", "ai strategy", "ai programme", "ai initiative",
                                "generative ai", "machine learning", "data platform", "data lakehouse",
                                "data warehouse", "predictive analytics", "ai use case", "ai adoption",
                                "data-driven", "analytics platform", "copilot rollout", "ai pilot",
                                "ai agents", "ai agent", "agentic", "large language model", "genai", "gen ai",
                                "ai transformation", "ai roadmap", "ai investment", "ai platform", "data strategy",
                                "digital strategy", "ai capabilities", "ai solutions", "ai tools"),
                        List.of("what is artificial intelligence", "explainer")),
                new Definition(
                        "data.hiring", DATA,
                        "Is the company hiring data, analytics or machine-learning staff?",
                        """
                        Counts: open roles for data engineering, analytics engineering, data science or machine \
                        learning inside this company.

                        Does not count: a consultancy advertising data roles for client projects, or a role that \
                        is not part of this company's own function.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "capability", DecayClass.HIRING,
                        List.of("data engineer", "data scientist", "machine learning engineer", "ml engineer",
                                "data architect", "analytics engineer", "head of data", "chief data officer",
                                "data analyst", "business intelligence", "data platform engineer"),
                        List.of("internship", "for our client", "client site")),
                new Definition(
                        "data.governance", DATA,
                        "Is the company running a data governance, quality or master-data programme?",
                        """
                        Counts: a named governance or data-quality programme, a master data management initiative, \
                        or a stated single-source-of-truth programme.

                        Does not count: a general statement that data is important.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "compliance", DecayClass.STRUCTURAL,
                        List.of("data governance", "master data management", "data quality", "data catalogue",
                                "data catalog", "data lineage", "single source of truth", "data stewardship",
                                "data management programme"),
                        List.of("what is data governance")));
    }
}
