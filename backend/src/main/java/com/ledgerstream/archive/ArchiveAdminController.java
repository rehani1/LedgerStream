package com.ledgerstream.archive;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin/archive")
public class ArchiveAdminController {

	private final ArchiveService archiveService;

	public ArchiveAdminController(ArchiveService archiveService) {
		this.archiveService = archiveService;
	}

	@PostMapping("/portfolio-snapshots")
	public ArchiveExportResponse exportPortfolioSnapshots(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		return archiveService.exportPortfolioSnapshots(date);
	}
}
