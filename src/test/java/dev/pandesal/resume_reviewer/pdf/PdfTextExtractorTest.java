package dev.pandesal.resume_reviewer.pdf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

	private final PdfTextExtractor extractor = new PdfTextExtractor();

	@TempDir
	Path tempDir;

	@Test
	void rejectsFileWithoutPdfMagicBytes() throws IOException {
		File file = tempDir.resolve("fake.pdf").toFile();
		Files.writeString(file.toPath(), "MZ not a pdf", StandardCharsets.US_ASCII);

		assertThat(PdfTextExtractor.hasPdfMagic(file)).isFalse();
		assertThatThrownBy(() -> extractor.extract(file)).isInstanceOf(IOException.class);
	}

	@Test
	void rejectsTooManyPages() throws IOException {
		File file = tempDir.resolve("long.pdf").toFile();
		try (PDDocument document = new PDDocument()) {
			for (int i = 0; i <= PdfTextExtractor.MAX_PAGES; i++) {
				document.addPage(new PDPage());
			}
			document.save(file);
		}

		assertThatThrownBy(() -> extractor.extract(file))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("too many pages");
	}

	@Test
	void extractsTextFromValidPdf() throws IOException {
		File file = tempDir.resolve("ok.pdf").toFile();
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage());
			document.save(file);
		}

		assertThat(PdfTextExtractor.hasPdfMagic(file)).isTrue();
		assertThat(extractor.extract(file)).isNotNull();
	}
}