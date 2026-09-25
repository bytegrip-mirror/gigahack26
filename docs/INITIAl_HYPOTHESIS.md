# AI-Powered B2B Sales Intelligence Platform
**Prepared for: Orange Systems Hackathon**

## 1. Core Concept
This platform is a continuous, automated sales intelligence engine. It dynamically maps Orange Systems's evolving service catalog and Ideal Customer Profiles (ICPs) against real-time business signals scraped from the web. Instead of relying on a single, expensive LLM for every task, the architecture uses a two-tiered AI approach: an ultra-fast **System One classifier** to score and categorize raw data, followed by a **generative LLM** to draft highly personalized, brand-aligned outreach.

## 2. Dynamic Configuration & Data Ingestion
* **Dynamic Service & ICP Mapping:** Sales teams can instantly define and update their Ideal Customer Profiles (industry, size, geography) and the specific services Orange offers (e.g., Agentic Process Automation, Cybersecurity). 
* **Automated Data Scraping:** The system utilizes an hourly cronjob to crawl configured sources (Crunchbase, LinkedIn, company newsrooms, career pages, NewsAPI). 
* **Configurable Signal Weights:** Users assign priority weights to different signals (e.g., hiring an RPA developer is "High," a generic mention of efficiency is "Medium").

## 3. AI/ML Layer: The Two-Tiered Approach
To process massive amounts of scraped data efficiently and accurately, the platform splits the AI workload into two distinct phases.

### Phase A: The Signal Triage Layer (Jev Classifier)
Instead of forcing a standard generative model (like ChatGPT or Claude) to classify data, the platform integrates **Jev**, a specialized "System One" AI model launched in September 2026 by TypeSafe AI. 

* **Structured Output Without "Slop":** Unlike standard LLMs that generate text token-by-token, Jev reads messy, unstructured scraped data and returns strictly typed values—such as a category, a relevance score, or a yes/no flag. 
* **Massive Cost & Speed Advantages:** Because Jev evaluates inputs using parallel sampling rather than sequential generation, it is up to 40 to 400 times cheaper and faster than frontier generative LLMs on classification tasks. This makes hourly, large-scale web scraping financially viable.
* **Zero-Shot Dynamic Taxonomy:** Jev performs zero-shot text classification based on plain-language labels. When Orange Systems adds a new service or changes its ICP, the sales team simply types the new categories into the dashboard. Jev immediately adapts to the new parameters without requiring any model fine-tuning.
* **Calibrated Confidence:** Jev outputs a calibrated probability score for every signal it detects. If a scraped news article yields a 0.95 confidence score for "Needs Process Automation," it is automatically routed to the hot leads queue.

### Phase B: The Generative Outreach Layer (Traditional LLM)
Jev acts as the upfront gatekeeper. Only the structured, highly qualified signals that pass the triage layer are handed off to a traditional LLM (e.g., OpenAI, Anthropic, or Llama). 

* **Brandbook Integration:** The LLM is strictly prompted with Orange Systems's dynamically set brand voice and brandbook.
* **Anti-Slop Generation:** Because the LLM is fed clean, structured context from Jev (e.g., `{"Company": "X", "Signal": "Hiring RPA Dev", "Service Match": "Automation"}`), it generates highly relevant, human-sounding emails or call scripts, completely avoiding generic AI filler.

## 4. User Experience & Dashboard
* **Unified Pipeline View:** A React-based dashboard cleanly displays all identified leads alongside the specific source links (the "what" and the "where").
* **Evidence-Based Scoring:** Sales reps can see exactly *why* a company was flagged, complete with the specific Jev classification score and the text snippet that triggered it.
* **One-Click Action:** Reps can review the AI-generated, brand-compliant email drafts or call scripts directly in the dashboard and export them to their CRM (e.g., HubSpot).

## 5. Hackathon Judging Alignment
* **AI/ML Innovation (20%):** Demonstrates architectural sophistication by pioneering the use of a "System One" model (Jev) for triage, solving the cost and latency bottlenecks of traditional LLM pipelines.
* **Configurability (20%):** The zero-shot capabilities of the classifier allow the ICP and Service lists to be entirely dynamic without breaking the ML pipeline.
* **Signal Relevance (25%):** Using a dedicated, calibrated classifier heavily reduces false positives compared to prompting standard generative models for categorization.