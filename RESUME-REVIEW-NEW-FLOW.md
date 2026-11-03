# Context
New flow for the resume review process (Evaluation process only)

# Flow
## From User Input (Discord, retain existing input process)
1. Validate file type (PDF only) and size (max 5MB)
2. Check if file is already in the database (by hash)
   1. If yes, return the existing evaluation report
   2. If no, proceed to evaluation
   - The hash is always computed from the **original** file (used for both
     the cache lookup and the stored `file_hash`).
   - The **redacted** resume text (PII removed) is what gets base64-encoded
     and stored.
3. Resume in PDF format is received
4. Using `pdfbox`'s `PDFTextStripper`, extract the text content from the PDF file
   1. Sample Code:
      ```
        private static String extractTextFromPdf(String filePath) throws IOException {
           if (filePath != null) {
               filePath = filePath.replace("\"", "").trim();
           }
   
           File file = new File(filePath);
           try (PDDocument document = Loader.loadPDF(file)) {
               PDFTextStripper pdfStripper = new PDFTextStripper();
               return pdfStripper.getText(document);
        }
   }
      ```
5. Build LLM prompt
   1. For System, perform escape:
        ```
        You are an expert ATS (Applicant Tracking System) Evaluator and Technical Recruiter.
        Your task is to independently analyze the candidate's provided resume text and dynamically calculate objective, realistic scores based on standard technical recruitment benchmarks.
        
        Before outputting the final scores, critically evaluate the resume across these explicit rubrics:
        1. Structure & Parsing (Max 25): Deduct points for multi-column layouts, stylized headers, text boxes, or non-standard list indicators that hinder parsing.
        2. Contact Information (Max 10): Deduct points if name, email, location, phone number, or standard professional links (e.g. LinkedIn/GitHub with full protocols) are poorly formatted or missing.
        3. Keywords & Alignment (Max 30): Grade based on density and exact matches of core target technologies, frameworks, and job-title keywords vs industry baseline expectations.
        4. Impact & Metrics (Max 35): Grade heavily on quantified accomplishments (percentages, numbers, values) vs passive, responsibility-based duty phrases.
        
        Do not reference or assume external findings or pre-computed scores. Base all deductions and evaluations purely on the input resume text. Do not invent facts that are not present.
        
        Tone and Style Guardrails:
        - Write in a direct, plain, and straightforward manner.
        - Avoid overly complex vocabulary, buzzwords, or dense corporate jargon.
        - Keep sentences short, active, and highly scannable.
        - Ensure every bullet point is punchy and gets straight to the point.
        - Turn your Reasoning or thinking to off, disable long thinking and set the codex's api call to disable reasoning, if not possible set it to the lowest. Our priority is fast prompt and simple prompts and response
          enable_thinking: false
          --reasoning-budget 8192
          --reasoning-budget-message "Time to stop thinking. Give the final answer or make the tool call now."
        
        Respond using EXACTLY this format, starting with the numerical ratings, followed by markdown bullet lists under each header:
        
        OVERALL_ATS_SCORE: [Calculate and insert the sum of the detailed ratings here]/100
        DETAILED_RATINGS:
        - Structure & Parsing: [Insert your calculated score here]/25
        - Contact Information: [Insert your calculated score here]/10
        - Keywords & Alignment: [Insert your calculated score here]/30
        - Impact & Metrics: [Insert your calculated score here]/35
        
        CRITICAL_BLOCKERS:
        - ...
        
        STRENGTHS:
        - ...
        
        RECOMMENDATIONS:
        - ...
        ```
   2. For User, append the extracted resume text to the end, and perform escape:
        ```
        Please evaluate the following candidate resume text.s
        Analyze its structure, contact layout, technical keywords, and the impact of its bullet points.
                
        Resume text:
        ```
   3. Payload:
        ```
        {
          "model": "auto",
          "temperature": 0.3,
          "reasoning_effort": "none",
          "messages": [
            {
              "role": "system",
              "content": "{escapedSystem}"
            },
            {
              "role": "user",
              "content": "{fullUserContent}"
            }
          ],
          "max_tokens": 2000
        }
      ```
6. Send the payload to the LLM for evaluation
7. Receive the response from the LLM, which includes:
   - OVERALL_ATS_SCORE
   - DETAILED_RATINGS
   - CRITICAL_BLOCKERS
   - STRENGTHS
   - RECOMMENDATIONS
8. Parse the response and return the structured evaluation report to the user in Discord.
9. Persist the evaluation results in the database for future reference and analysis.
   1. For the persistence, store the following:
      - Encoded resume text (base64) — the **redacted** text
      - Resume file hash (enforces uniqueness regardless of file name) — from the **original** file
      - Full markdown report
      - Overall score
      - Detailed ratings
      - Critical blockers
      - Strengths
      - Recommendations

## Confirmation gate (Discord)
After the file is validated and downloaded, the bot replies **ephemerally**
with two buttons:
- **Continue** — run the evaluation and cache the result (step 9).
- **Cancel** — discard the submission without evaluating.

`ReviewConfirmation` holds the file bytes (keyed by UUID) and blocks on a
virtual thread for up to `review.confirmation-timeout-seconds` (default 45).
No click in time → the review is cancelled (treated like **Cancel**).

## Role suitability (optional)
`/resume-review review` accepts an optional `role_suitability` option: a
comma-separated list of roles, e.g. `software engineer,QA,tester`.

- Roles are trimmed, blanks dropped, and duplicates removed
  (case-insensitive). At most **5** roles are accepted.
- When present **and the resume is not already cached**, a **separate LLM
  call** runs *after* the ATS report is posted. It scores the resume against
  each role (0-100) with a short reasoning and posts the result as a separate
  message.
- If the resume was already reviewed, the role suitability analysis does
  **not** run; a short "skipped: already reviewed" note is posted instead.
- Role suitability results are not persisted.
- A role suitability failure is non-fatal: the ATS report is already posted
  and an error message is posted in its place.

# Instructions
1. Implement one group of changes at a time. Wait for confirmation before proceeding to the next set of changes.
2. Ensure that the evaluation process is fully automated and does not require any manual intervention.
3. Fully remove localized scoring logic and replace it with the LLM-based evaluation process.
4. If LLM is not available, return a message to the user indicating that the evaluation cannot be completed at this time.
5. Ensure that the evaluation report is returned in a structured format that can be easily parsed and displayed in Discord.
6. Re-create the database schema when necessary to accommodate the new evaluation results and ensure that all relevant data is stored for future reference.

# Escape Implementation (sample)
```java
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

```