package com.ledgerstream.archive;

class DisabledArchiveSink implements ArchiveSink {

	@Override
	public ArchiveLocation write(String key, byte[] content, String contentType) {
		throw new ArchiveException("Archive exports are disabled");
	}
}
