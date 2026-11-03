package dev.pandesal.resume_reviewer.review;

/**
 * The final, human-readable result of a resume review (ATS-compliance focus),
 * produced entirely by the LLM.
 *
 * @param overallScore     0-100 ATS score (sum of the detailed ratings)
 * @param detailedRatings  per-category ratings (markdown bullet list)
 * @param criticalBlockers markdown bullet list of critical ATS blockers
 * @param strengths        markdown bullet list of strengths
 * @param recommendations  markdown bullet list of recommendations
 * @param report           full markdown evaluation report
 */
public record ReviewResult(
	double overallScore,
	String detailedRatings,
	String criticalBlockers,
	String strengths,
	String recommendations,
	String report
) {
}
