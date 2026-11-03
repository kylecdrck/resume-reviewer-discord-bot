package dev.pandesal.resume_reviewer.review;

import java.util.List;

/**
 * The LLM's role-suitability analysis of a resume against the requested roles.
 *
 * @param assessments one assessment per requested role, in the requested order
 */
public record RoleSuitabilityResult(List<RoleAssessment> assessments) {
}