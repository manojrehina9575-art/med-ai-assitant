package com.medai.longitudinal.matching;

import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.StructuredFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FindingMatcher {

    public MatchResult match(List<StructuredFinding> priorFindings, List<StructuredFinding> currentFindings) {
        List<StructuredFinding> prior = priorFindings == null ? List.of() : priorFindings;
        List<StructuredFinding> current = currentFindings == null ? List.of() : currentFindings;
        List<Candidate> candidates = candidates(prior, current);

        Set<Integer> matchedPrior = new HashSet<>();
        Set<Integer> matchedCurrent = new HashSet<>();
        List<Match> matches = new ArrayList<>();

        while (true) {
            Candidate next = candidates.stream()
                    .filter(candidate -> !matchedPrior.contains(candidate.priorIndex()))
                    .filter(candidate -> !matchedCurrent.contains(candidate.currentIndex()))
                    .filter(candidate -> uniquelyBest(candidate, candidates, matchedPrior, matchedCurrent))
                    .max(Comparator.comparingInt(Candidate::score)
                            .thenComparingInt(candidate -> -candidate.priorIndex())
                            .thenComparingInt(candidate -> -candidate.currentIndex()))
                    .orElse(null);

            if (next == null) {
                break;
            }

            matchedPrior.add(next.priorIndex());
            matchedCurrent.add(next.currentIndex());
            matches.add(new Match(prior.get(next.priorIndex()), current.get(next.currentIndex())));
        }

        List<StructuredFinding> unmatchedPrior = unmatched(prior, matchedPrior);
        List<StructuredFinding> unmatchedCurrent = unmatched(current, matchedCurrent);
        return new MatchResult(matches, unmatchedPrior, unmatchedCurrent);
    }

    private List<Candidate> candidates(List<StructuredFinding> prior, List<StructuredFinding> current) {
        List<Candidate> candidates = new ArrayList<>();
        for (int priorIndex = 0; priorIndex < prior.size(); priorIndex++) {
            for (int currentIndex = 0; currentIndex < current.size(); currentIndex++) {
                candidate(priorIndex, prior.get(priorIndex), currentIndex, current.get(currentIndex))
                        .ifPresent(candidates::add);
            }
        }
        return candidates;
    }

    private java.util.Optional<Candidate> candidate(
            int priorIndex,
            StructuredFinding prior,
            int currentIndex,
            StructuredFinding current
    ) {
        if (prior.findingType() != current.findingType()) {
            return java.util.Optional.empty();
        }
        if (prior.anatomy() == null || current.anatomy() == null || prior.anatomy() != current.anatomy()) {
            return java.util.Optional.empty();
        }
        if (!sideCompatible(prior.side(), current.side())) {
            return java.util.Optional.empty();
        }

        int score = 100;
        score += sideScore(prior.side(), current.side());
        score += regionScore(prior.region(), current.region());
        if (prior.certainty() == current.certainty()) {
            score += 2;
        }
        score += Math.min(5, sharedTermCount(prior, current));
        return java.util.Optional.of(new Candidate(priorIndex, currentIndex, score));
    }

    private boolean uniquelyBest(
            Candidate candidate,
            List<Candidate> candidates,
            Set<Integer> matchedPrior,
            Set<Integer> matchedCurrent
    ) {
        int bestForPrior = bestScoreForPrior(candidate, candidates, matchedPrior, matchedCurrent);
        int bestForCurrent = bestScoreForCurrent(candidate, candidates, matchedPrior, matchedCurrent);
        if (candidate.score() != bestForPrior || candidate.score() != bestForCurrent) {
            return false;
        }

        long priorBestCount = candidates.stream()
                .filter(other -> !matchedCurrent.contains(other.currentIndex()))
                .filter(other -> other.priorIndex() == candidate.priorIndex())
                .filter(other -> other.score() == bestForPrior)
                .count();
        long currentBestCount = candidates.stream()
                .filter(other -> !matchedPrior.contains(other.priorIndex()))
                .filter(other -> other.currentIndex() == candidate.currentIndex())
                .filter(other -> other.score() == bestForCurrent)
                .count();
        return priorBestCount == 1 && currentBestCount == 1;
    }

    private int bestScoreForPrior(
            Candidate candidate,
            List<Candidate> candidates,
            Set<Integer> matchedPrior,
            Set<Integer> matchedCurrent
    ) {
        return candidates.stream()
                .filter(other -> !matchedPrior.contains(other.priorIndex()))
                .filter(other -> !matchedCurrent.contains(other.currentIndex()))
                .filter(other -> other.priorIndex() == candidate.priorIndex())
                .mapToInt(Candidate::score)
                .max()
                .orElse(Integer.MIN_VALUE);
    }

    private int bestScoreForCurrent(
            Candidate candidate,
            List<Candidate> candidates,
            Set<Integer> matchedPrior,
            Set<Integer> matchedCurrent
    ) {
        return candidates.stream()
                .filter(other -> !matchedPrior.contains(other.priorIndex()))
                .filter(other -> !matchedCurrent.contains(other.currentIndex()))
                .filter(other -> other.currentIndex() == candidate.currentIndex())
                .mapToInt(Candidate::score)
                .max()
                .orElse(Integer.MIN_VALUE);
    }

    private List<StructuredFinding> unmatched(List<StructuredFinding> findings, Set<Integer> matchedIndexes) {
        List<StructuredFinding> unmatched = new ArrayList<>();
        for (int index = 0; index < findings.size(); index++) {
            if (!matchedIndexes.contains(index)) {
                unmatched.add(findings.get(index));
            }
        }
        return unmatched;
    }

    private boolean sideCompatible(AnatomicalSide prior, AnatomicalSide current) {
        if (prior == AnatomicalSide.UNSPECIFIED || current == AnatomicalSide.UNSPECIFIED) {
            return true;
        }
        return prior == current;
    }

    private int sideScore(AnatomicalSide prior, AnatomicalSide current) {
        if (prior == current && prior != AnatomicalSide.UNSPECIFIED) {
            return 40;
        }
        if (prior == AnatomicalSide.UNSPECIFIED && current == AnatomicalSide.UNSPECIFIED) {
            return 5;
        }
        return 10;
    }

    private int regionScore(AnatomicalRegion prior, AnatomicalRegion current) {
        if (prior == current && prior != AnatomicalRegion.UNSPECIFIED) {
            return 30;
        }
        if (prior == AnatomicalRegion.UNSPECIFIED || current == AnatomicalRegion.UNSPECIFIED) {
            return 10;
        }
        return 1;
    }

    private int sharedTermCount(StructuredFinding prior, StructuredFinding current) {
        Set<String> sharedTerms = new HashSet<>(prior.normalizedTerms());
        sharedTerms.retainAll(current.normalizedTerms());
        return sharedTerms.size();
    }

    private record Candidate(int priorIndex, int currentIndex, int score) {
    }

    public record Match(StructuredFinding priorFinding, StructuredFinding currentFinding) {
    }

    public record MatchResult(
            List<Match> matches,
            List<StructuredFinding> unmatchedPrior,
            List<StructuredFinding> unmatchedCurrent
    ) {
        public MatchResult {
            matches = matches == null ? List.of() : List.copyOf(matches);
            unmatchedPrior = unmatchedPrior == null ? List.of() : List.copyOf(unmatchedPrior);
            unmatchedCurrent = unmatchedCurrent == null ? List.of() : List.copyOf(unmatchedCurrent);
        }
    }
}
