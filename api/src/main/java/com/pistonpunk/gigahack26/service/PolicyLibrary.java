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
import java.util.Set;
import java.util.UUID;

@Service
public class PolicyLibrary {

    public static final String AUTOMATION = "Intelligent Automation";
    public static final String SECURITY = "Cybersecurity";
    public static final String DATA = "Data & AI";
    public static final String CLOUD = "Cloud & Infrastructure";
    public static final String ERP = "ERP & Business Applications";
    public static final String DIGITAL = "Digital Customer Experience";

    private final PolicyQuestionRepository questions;
    private final Clock clock;

    public PolicyLibrary(PolicyQuestionRepository questions, Clock clock) {
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional
    public boolean ensureSeeded() {
        return ensureSeeded(true);
    }

    @Transactional
    public boolean ensureSeeded(boolean syncExisting) {
        boolean changed = false;
        for (Definition definition : definitions()) {
            Optional<PolicyQuestion> existing = questions.findByKey(definition.key());
            if (existing.isEmpty()) {
                questions.save(toEntity(definition));
                changed = true;
                continue;
            }
            PolicyQuestion question = existing.get();
            changed |= differs(question, definition);
            question.revise(
                    definition.title(),
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
        return retire(declared()) || changed;
    }

    /**
     * A question the library no longer declares stops scoring.
     *
     * Removing a definition used to leave the stored row active forever, so a
     * question deleted from the library kept classifying the corpus and kept its
     * place on the board. It is deactivated rather than deleted, because the
     * signals, feedback and scores that point at it are still a record of what
     * the board said at the time.
     */
    private boolean retire(Set<String> declared) {
        boolean retired = false;
        for (PolicyQuestion question : questions.findAll()) {
            if (!declared.contains(question.getKey()) && question.isActive()) {
                question.revise(question.getTitle(), question.getText(), question.getInstructions(),
                        question.getWeightTier(), question.getPolarity(), question.getTheme(),
                        question.getDecayClass(), question.getCues(), question.getCounterCues(), false,
                        clock.instant());
                questions.save(question);
                retired = true;
            }
        }
        return retired;
    }

    private static Set<String> declared() {
        return definitions().stream().map(Definition::key).collect(java.util.stream.Collectors.toSet());
    }

    private static boolean differs(PolicyQuestion question, Definition definition) {
        return !question.getText().equals(definition.text())
                || !question.getTheme().equals(definition.theme())
                || question.getWeightTier() != definition.tier()
                || question.getPolarity() != definition.polarity()
                || question.getDecayClass() != definition.decayClass()
                || !sameTerms(question.getCues(), definition.cues())
                || !sameTerms(question.getCounterCues(), definition.counterCues());
    }

    /**
     * Cue lists are sets that happen to be stored as lists.
     *
     * An element collection has no column to order by, so the order that comes
     * back is whatever the database returns, and comparing the lists directly
     * reported a change on every boot. That mattered more than a wasted
     * comparison: a reported policy change sends the seeder down a path that
     * reclassified the whole corpus with the local engine and threw away the
     * model overlay it had just loaded.
     */
    static boolean sameTerms(List<String> stored, List<String> declared) {
        return stored.size() == declared.size() && Set.copyOf(stored).equals(Set.copyOf(declared));
    }

    public record Definition(String key, String title, String serviceLine, String text, String instructions,
                             WeightTier tier, Polarity polarity, String theme, DecayClass decayClass,
                             List<String> cues, List<String> counterCues) {
    }

    private PolicyQuestion toEntity(Definition definition) {
        return new PolicyQuestion(
                UUID.randomUUID(),
                definition.key(),
                definition.title(),
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
                        "automation.programme", "An announced efficiency programme", AUTOMATION,
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
                        "automation.hiring", "Hiring for process excellence", AUTOMATION,
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
                                "process lead", "transformation office", "operational excellence manager",
                                "automation lead", "head of automation", "digital transformation",
                                "shared services", "continuous improvement"),
                        List.of("internship", "intern,", "unpaid", "student worker")),
                new Definition(
                        "automation.insourcing", "Building automation in-house", AUTOMATION,
                        "Does the company sell automation, or build it as a product of its own rather than buying the capability?",
                        """
                        Counts: the company states it sells or provides automation to others, runs an internal \
                        automation factory as a business, or has an automation product of its own. This is a company \
                        that will build rather than buy, so it counts against calling them.

                        Does not count: a company that has an internal team overseeing external delivery partners, \
                        or that hires, uses or runs automation inside its own business. Internal capability is the \
                        normal state of a large company and is often exactly the buying signal we are looking for.""",
                        WeightTier.HIGH, Polarity.NEGATIVE, "in-house", DecayClass.STRUCTURAL,
                        List.of("in-house automation", "internal automation team", "automation factory",
                                "in-house developed", "our automation platform", "our rpa platform",
                                "our process mining platform", "our low-code platform",
                                "our intelligent automation platform", "our automation software",
                                "we build our own", "developed in-house",
                                "build our own automation", "automation centre of excellence in-house",
                                "leading rpa platform", "leading process mining platform",
                                "automation platform for enterprises", "customers use our platform",
                                "automation platform that", "customers automate with"),
                        List.of("delivery partner", "systems integrator", "third-party provider", "outsourced",
                                "centre of excellence is", "center of excellence is", "what is a centre of excellence",
                                "explainer", "how to set up", "evaluating", "considering", "selecting a platform",
                                "market guide", "gartner", "forrester", "magic quadrant", "we compared")),
                new Definition(
                        "automation.ssc", "Consolidating shared service centres", AUTOMATION,
                        "Is the company consolidating shared service centres or business services into a captive operation?",
                        """
                        Counts: moving scattered finance, HR or IT operations into one shared service centre, a \
                        global business services organisation, or a captive centre — the classic precursor to an \
                        automation programme, because consolidation is what makes volume worth automating.

                        Does not count: a company selling shared-service outsourcing to others.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "operating-model", DecayClass.STRUCTURAL,
                        List.of("shared service centre", "shared services centre", "shared services center",
                                "global business services", "global capability centre", "captive centre",
                                "captive operation", "consolidating shared services", "ssc consolidation",
                                "business services centre", "consolidate its shared"),
                        List.of("provider of shared services", "we provide shared services")),
                new Definition(
                        "automation.process_mining", "Adopting process mining", AUTOMATION,
                        "Is the company adopting process mining, task mining or process intelligence?",
                        """
                        Counts: a named process-mining platform or a stated process-discovery programme. Naming the \
                        tooling is the strongest form of this signal, because it means the budget is already approved.

                        Does not count: a vendor describing its own product to the market.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "capability", DecayClass.STRUCTURAL,
                        List.of("process mining", "task mining", "process discovery", "process intelligence",
                                "celonis", "signavio", "process mining platform", "process mining initiative"),
                        List.of("our process mining", "process mining vendor", "leading process mining")),
                new Definition(
                        "data.platform_migration", "A platform migration under way", DATA,
                        "Is the company migrating a core platform — ERP, core banking or a data platform?",
                        """
                        Counts: a named migration with a destination or a deadline. A migration is the moment a \
                        buyer is forced to re-examine the processes around the system, which is when services get \
                        bought.

                        Does not count: a vendor announcing a product.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "platform", DecayClass.EVENT,
                        List.of("s/4hana", "sap s/4", "erp migration", "erp transformation",
                                "core banking platform", "core banking replacement", "data platform migration",
                                "cloud migration programme", "replatforming", "migration programme",
                                "system migration", "modernising its core", "modernizing its core"),
                        List.of("our platform", "vendor", "customers migrate")),
                new Definition(
                        "data.ai_governance", "An AI governance mandate", DATA,
                        "Has the company stood up AI governance, a responsible-AI policy or an AI risk function?",
                        """
                        Counts: a named AI governance body, a responsible-AI policy, or work to meet the EU AI Act. \
                        It precedes the spend: a company that has to govern models has to know what models it runs.

                        Does not count: a think-piece about AI risk.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "governance", DecayClass.EVENT,
                        List.of("ai governance", "responsible ai", "ai ethics", "ai risk assessment",
                                "ai act", "eu ai act", "model governance", "ai policy", "ai oversight board",
                                "ai assurance"),
                        List.of("what is ai governance", "explainer", "commentary")),
                new Definition(
                        "automation.vendor_lock", "Already committed to an automation vendor", AUTOMATION,
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
                        "security.leadership", "A new security leader", SECURITY,
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
                        "security.compliance", "A named compliance obligation", SECURITY,
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
                        "security.incident", "A reported security incident", SECURITY,
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
                        "security.insourcing", "Building security operations in-house", SECURITY,
                        "Does the company sell security operations as a service, or run its own as a product of its own?",
                        """
                        Counts: the company sells security operations, managed detection or a security product of \
                        its own. That is a provider of the capability rather than a buyer of it, so it counts \
                        against calling them.

                        Does not count: a company running an internal security team, or a security operations \
                        centre of its own. Most large enterprises have one; it does not stop them buying incident \
                        response, compliance work or specialist engineering.""",
                        WeightTier.HIGH, Polarity.NEGATIVE, "in-house", DecayClass.STRUCTURAL,
                        List.of("in-house soc", "own soc", "in-house security team", "internal soc team",
                                "we run our own soc", "internal security operations centre",
                                "we handle security ourselves", "security operations centre in-house",
                                "our security platform", "our siem", "our soar platform", "our edr platform",
                                "our xdr platform", "our threat intelligence platform", "our managed detection",
                                "we protect our customers", "protect your business", "our cybersecurity solutions",
                                "our security products", "customers use our platform", "our security operations platform"),
                        List.of("managed security provider", "outsourced security", "what is a soc",
                                "security operations centre is", "center is", "explainer", "forrester",
                                "gartner", "magic quadrant", "market guide", "we compared")),
                new Definition(
                        "data.insourcing", "Selling a data or AI platform of its own", DATA,
                        "Is the company a vendor of data, analytics or AI platforms rather than a buyer of them?",
                        """
                        Counts: the company describes a data, analytics or AI product of its own that customers \
                        buy, or positions itself as a provider of the capability this question looks for in a buyer. \
                        A vendor writes about its own category constantly, so its pages outrank a real buyer's and \
                        it lands in the queue on its own marketing.

                        Does not count: a company that has built an internal data team to run platforms it bought.""",
                        WeightTier.HIGH, Polarity.NEGATIVE, "in-house", DecayClass.STRUCTURAL,
                        List.of("our data platform", "our analytics platform", "our ai platform",
                                "our machine learning platform", "our ml platform", "our data fabric",
                                "our data warehouse product", "our lakehouse", "customers use our platform",
                                "our ai products", "our genai platform", "our copilot product",
                                "leading data platform", "leading analytics platform", "leading ai platform",
                                "data platform for enterprises", "analytics platform that", "we help customers",
                                "our customers use", "platform that lets you"),
                        List.of("built on", "powered by", "we use", "our deployment of", "third-party")),
                new Definition(
                        "data.initiative", "A data or AI initiative", DATA,
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
                                "ai capabilities", "generative ai"),
                        List.of("what is artificial intelligence", "explainer")),
                new Definition(
                        "data.hiring", "Hiring data or ML staff", DATA,
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
                        "data.governance", "A data governance programme", DATA,
                        "Is the company running a data governance, quality or master-data programme?",
                        """
                        Counts: a named governance or data-quality programme, a master data management initiative, \
                        or a stated single-source-of-truth programme.

                        Does not count: a general statement that data is important.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "compliance", DecayClass.STRUCTURAL,
                        List.of("data governance", "master data management", "data quality", "data catalogue",
                                "data catalog", "data lineage", "single source of truth", "data stewardship",
                                "data management programme"),
                        List.of("what is data governance")),
                new Definition(
                        "cloud.migration", "A cloud migration programme", CLOUD,
                        "Is the company moving workloads, applications or its own data centres to a public cloud?",
                        """
                        Counts: a named migration or cloud adoption programme, a stated target to move a share of \
                        the estate, a data-centre exit, or a published timeline for leaving a hosting contract.

                        Does not count: a product that runs on someone else's cloud, a partner's case study about \
                        work done for another company, or a general statement that the company "uses the cloud". \
                        The move must be this company's own and must be under way.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "platform", DecayClass.STRUCTURAL,
                        List.of("cloud migration", "migrating to the cloud", "move to aws", "moving to aws",
                                "microsoft azure", "google cloud", "cloud-first", "cloud first",
                                "cloud adoption", "cloud transformation", "replatform", "lift and shift",
                                "data centre exit", "data center exit", "exit our data centre",
                                "decommission data centre", "cloud strategy", "multi-cloud", "hybrid cloud",
                                "cloud-native", "cloud native", "containerisation", "containerization",
                                "kubernetes", "modernise the estate", "modernize the estate"),
                        List.of("cloud computing market", "what is the cloud")),
                new Definition(
                        "cloud.hiring", "Hiring cloud and platform engineers", CLOUD,
                        "Is the company hiring cloud, DevOps, SRE or platform engineers to run its own estate?",
                        """
                        Counts: roles that build or operate this company's own platform — cloud engineer, platform \
                        engineer, site reliability engineer, DevOps engineer, infrastructure architect.

                        Does not count: a vendor or consultancy advertising the same roles for client projects, or \
                        a sales role with "cloud" in the title.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "capability", DecayClass.HIRING,
                        List.of("cloud engineer", "platform engineer", "site reliability engineer", "sre",
                                "devops engineer", "cloud architect", "infrastructure engineer",
                                "cloud operations", "platform team", "head of platform", "cloud security engineer",
                                "infrastructure architect", "cloud infrastructure"),
                        List.of("internship", "for our client", "client site", "cloud sales")),
                new Definition(
                        "cloud.exit", "Bringing the estate back in-house", CLOUD,
                        "Is the company taking infrastructure work back from a supplier, or building a platform of its own to sell?",
                        """
                        Counts: the company says it is ending an outsourcing or hosting arrangement to run the \
                        estate itself, or sells cloud and infrastructure services as its own product.

                        Does not count: a company that merely prefers one cloud over another.""",
                        WeightTier.MEDIUM, Polarity.NEGATIVE, "in-house", DecayClass.STRUCTURAL,
                        List.of("in-house infrastructure", "in house platform", "bring infrastructure in-house",
                                "end the outsourcing", "insource", "we provide cloud services",
                                "our cloud platform", "managed cloud provider", "we sell cloud",
                                "own data centre", "own data center", "build our own platform"),
                        List.of("in-house team of one")),
                new Definition(
                        "erp.programme", "A core system replacement", ERP,
                        "Is the company replacing or upgrading its ERP, finance or core operating system?",
                        """
                        Counts: a named ERP programme, an upgrade to a named release, a stated go-live date, or a \
                        published plan to replace the system of record for finance, supply chain or HR.

                        Does not count: a vendor describing an implementation it delivered for a customer, or a \
                        general statement about "digitalising processes". The replacement must be this company's.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "platform", DecayClass.STRUCTURAL,
                        List.of("erp", "enterprise resource planning", "sap s/4hana", "s/4hana", "sap hana",
                                "sap implementation", "oracle fusion", "microsoft dynamics", "dynamics 365",
                                "netsuite", "workday", "sap go-live", "system integrator", "core system",
                                "system of record", "upgrade our erp", "erp rollout", "finance transformation"),
                        List.of("erp market", "what is erp")),
                new Definition(
                        "erp.hiring", "Hiring for a core-system rollout", ERP,
                        "Is the company hiring people to run or deliver an ERP, finance-systems or business-applications programme?",
                        """
                        Counts: roles that select, deliver or run the core system — ERP analyst, SAP consultant on \
                        the company's own payroll, finance systems manager, business applications lead.

                        Does not count: a consultancy advertising the same roles for client engagements, or a \
                        support-desk role with no programme behind it.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "capability", DecayClass.HIRING,
                        List.of("erp analyst", "sap consultant", "erp manager", "finance systems",
                                "business systems analyst", "erp developer", "sap analyst", "workday analyst",
                                "erp project manager", "head of business applications", "erp lead",
                                "systems accountant", "finance systems manager"),
                        List.of("internship", "for our client", "client site")),
                new Definition(
                        "erp.insourcing", "Building or selling the system instead of buying", ERP,
                        "Does the company sell ERP or business-application software, or run its own implementation practice?",
                        """
                        Counts: the company states it provides ERP implementation, or sells a business-applications \
                        product of its own.

                        Does not count: a company that merely has an ERP system.""",
                        WeightTier.MEDIUM, Polarity.NEGATIVE, "in-house", DecayClass.STRUCTURAL,
                        List.of("we implement sap", "our erp product", "we provide erp", "erp implementation partner",
                                "sap partner", "we sell erp", "our business applications platform",
                                "system integrator for", "implementation practice"),
                        List.of("our erp system is")),
                new Definition(
                        "digital.channel", "A new customer channel", DIGITAL,
                        "Is the company launching or rebuilding a customer-facing digital channel — an app, a portal or a self-service journey?",
                        """
                        Counts: a named launch or rebuild of a customer app, portal or self-service journey, with \
                        a date or a stated scope.

                        Does not count: a marketing campaign, a social-media presence, or a supplier's case study \
                        about a channel it built for someone else.""",
                        WeightTier.HIGH, Polarity.POSITIVE, "initiative", DecayClass.EVENT,
                        List.of("new mobile app", "launch our app", "customer portal", "digital channel",
                                "self-service", "self service portal", "online journey", "customer app",
                                "digital experience", "new website platform", "customer platform",
                                "digital-first", "rebuild our website", "omnichannel", "mobile banking app",
                                "customer facing platform", "digital onboarding"),
                        List.of("app store ranking", "download our app")),
                new Definition(
                        "digital.hiring", "Hiring digital product people", DIGITAL,
                        "Is the company hiring product, design or digital-delivery people for its own channels?",
                        """
                        Counts: roles that own or build a customer-facing channel — product owner, product manager, \
                        UX or service designer, digital delivery lead, front-end engineer.

                        Does not count: an agency advertising the same roles for client work, or a marketing role \
                        that does not touch the channel.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "capability", DecayClass.HIRING,
                        List.of("product owner", "product manager", "ux designer", "service designer",
                                "digital product", "head of digital", "digital delivery", "front-end engineer",
                                "frontend engineer", "user researcher", "digital product manager",
                                "customer experience manager", "head of product"),
                        List.of("internship", "for our client", "for a client")),
                new Definition(
                        "digital.commerce", "Replatforming commerce", DIGITAL,
                        "Is the company moving or rebuilding its e-commerce or transactional platform?",
                        """
                        Counts: a named replatforming or commerce-system programme, a stated move to a new \
                        commerce platform, or a published plan to rebuild checkout or payments.

                        Does not count: a product page or an offer.""",
                        WeightTier.MEDIUM, Polarity.POSITIVE, "platform", DecayClass.STRUCTURAL,
                        List.of("replatform", "e-commerce platform", "ecommerce platform", "commerce platform",
                                "shopify", "magento", "salesforce commerce", "adobe commerce", "new checkout",
                                "payment platform", "digital commerce", "online store rebuild",
                                "headless commerce", "order management system"),
                        List.of("shop now", "add to cart")));
    }
}
