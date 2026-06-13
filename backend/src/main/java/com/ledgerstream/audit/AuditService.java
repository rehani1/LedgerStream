package com.ledgerstream.audit;

import java.util.Map;

import com.ledgerstream.domain.model.AuditEvent;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuditEventRepository auditEventRepository;

	public AuditService(AuditEventRepository auditEventRepository) {
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(User user, String action, String requestId, Map<String, Object> metadata) {
		AuditEvent event = new AuditEvent();
		event.setUser(user);
		event.setAction(action);
		event.setRequestId(requestId);
		event.setMetadata(metadata);
		auditEventRepository.save(event);
	}
}
