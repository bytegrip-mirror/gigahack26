package com.pistonpunk.gigahack26.outreach.application;

import com.pistonpunk.gigahack26.outreach.domain.Voice;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class TemplateDraftWriter implements DraftWriter {

    @Override
    public String id() {
        return "template-v2";
    }

    @Override
    public Composed write(Brief brief, List<OutreachDraft.Citation> citations) {
        Voice voice = brief.voice();
        boolean inmail = "inmail".equalsIgnoreCase(brief.channel());
        boolean shortForm = inmail || "short".equalsIgnoreCase(voice.length());

        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(opening(brief, shortForm));

        if (brief.findings().size() >= 2 && !shortForm) {
            paragraphs.add("Two independent signals like that usually mean someone is about to decide how much of "
                    + "this to build and how much to buy.");
        }

        paragraphs.add(capitalise(brief.proposition()) + ".");

        if (!shortForm) {
            paragraphs.add("Work like this usually takes about three weeks to scope properly, and it starts from "
                    + "evidence rather than a discovery workshop.");
        }

        paragraphs.add(voice.ask());

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) {
                body.append("\n\n");
            }
            body.append(paragraphs.get(i));
        }

        if (!inmail && voice.signOff() != null && !voice.signOff().isBlank()) {
            body.append("\n\n").append(voice.signOff());
        }
        if (!inmail && voice.signature() != null && !voice.signature().isBlank()) {
            body.append("\n").append(voice.signature());
        }

        return new Composed(subject(brief), sanitise(body.toString(), voice.avoid()), id());
    }

    private String opening(Brief brief, boolean shortForm) {
        List<String> findings = brief.findings();
        String greeting = "Hi" + (brief.voice().reader().equalsIgnoreCase("you") ? "," : ",");

        if (findings.isEmpty()) {
            return greeting + "\n\nI have been looking at " + brief.company()
                    + " and I have a question about how your team is approaching "
                    + brief.serviceLine().toLowerCase(Locale.ROOT) + " this year.";
        }

        StringBuilder text = new StringBuilder();
        text.append(greeting).append("\n\n");
        text.append("I have been looking at ").append(brief.company()).append(". ");

        if (findings.size() == 1) {
            text.append("It looks like ").append(findings.get(0)).append(".");
        } else if (shortForm) {
            text.append("It looks like ").append(findings.get(0)).append(".");
        } else {
            text.append("It looks like ").append(join(findings)).append(".");
        }
        return text.toString();
    }

    private static String join(List<String> findings) {
        if (findings.size() == 2) {
            return findings.get(0) + ", and that " + findings.get(1);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < findings.size(); i++) {
            if (i > 0) {
                text.append(i == findings.size() - 1 ? ", and that " : ", that ");
            }
            text.append(findings.get(i));
        }
        return text.toString();
    }

    private static String subject(Brief brief) {
        String theme = brief.serviceLine();
        if (brief.trigger() != null && !brief.trigger().isBlank()) {
            String head = brief.trigger().split("—")[0].trim();
            if (!head.isBlank()) {
                theme = head;
            }
        }
        return "%s — %s".formatted(brief.company(), theme);
    }

    static String sanitise(String body, List<String> avoid) {
        String cleaned = body;
        for (String phrase : avoid) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            cleaned = cleaned.replaceAll("(?i)" + java.util.regex.Pattern.quote(phrase.trim()), "");
        }
        return cleaned.replaceAll("[ \\t]{2,}", " ").replaceAll(" +([,.!?])", "$1").replaceAll("\n{3,}", "\n\n").trim();
    }

    private static String capitalise(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
