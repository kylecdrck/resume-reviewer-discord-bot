package dev.pandesal.resume_reviewer.pdf;

import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Inspects resume5.pdf: page count and per-page text length.
 */
class PdfStructureTest {

	private static final Path FILE = Path.of("D:/Kyle Cancio/Programming/Personal/resume5.pdf");

	@Test
	void inspect() throws Exception {
		if (!java.nio.file.Files.exists(FILE)) {
			System.out.println("not found");
			return;
		}
		try (PDDocument doc = Loader.loadPDF(FILE.toFile())) {
			System.out.println("pages=" + doc.getNumberOfPages());
			for (int i = 0; i < doc.getNumberOfPages(); i++) {
				PDPage page = doc.getPage(i);
				PDFTextStripper stripper = new PDFTextStripper();
				stripper.setStartPage(i + 1);
				stripper.setEndPage(i + 1);
				String text = stripper.getText(doc);
				System.out.println("page " + (i + 1) + ": textLen=" + text.length()
					+ " mediaBox=" + page.getMediaBox());
			}
		}
	}
}
