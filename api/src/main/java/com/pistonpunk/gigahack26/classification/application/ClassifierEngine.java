package com.pistonpunk.gigahack26.classification.application;


import com.pistonpunk.gigahack26.policy.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.policy.domain.Verdict;
import java.util.List;

public interface ClassifierEngine {

    String id();

    boolean available();

    List<Finding> classify(ClassificationRequest request);

    record ClassificationRequest(String companyName, PolicyQuestion question, String document) {
    }

    record Finding(Verdict verdict, double confidence, int spanStart, int spanEnd, String rationale) {
    }
}
