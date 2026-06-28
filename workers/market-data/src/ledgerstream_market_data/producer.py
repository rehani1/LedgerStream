from typing import List, Optional

from confluent_kafka import KafkaException, Producer


class ProducerError(Exception):
	"""Raised when Kafka delivery or flush fails."""


class KafkaMarketTickPublisher:
	def __init__(
		self,
		bootstrap_servers: str,
		security_protocol: Optional[str] = None,
		sasl_mechanism: Optional[str] = None,
		sasl_username: Optional[str] = None,
		sasl_password: Optional[str] = None,
	) -> None:
		config = {
			"bootstrap.servers": bootstrap_servers,
			"client.id": "ledgerstream-market-data",
			"enable.idempotence": True,
			"acks": "all",
		}
		if security_protocol:
			config["security.protocol"] = security_protocol
		if sasl_mechanism:
			config["sasl.mechanism"] = sasl_mechanism
		if sasl_username:
			config["sasl.username"] = sasl_username
		if sasl_password:
			config["sasl.password"] = sasl_password
		self._producer = Producer(config)
		self._errors: List[str] = []

	def publish(self, topic: str, key: str, payload: str) -> None:
		try:
			self._producer.produce(
				topic=topic,
				key=key,
				value=payload.encode("utf-8"),
				callback=self._delivery_callback,
			)
			self._producer.poll(0)
		except BufferError as ex:
			raise ProducerError("Kafka producer buffer is full") from ex
		except KafkaException as ex:
			raise ProducerError(f"Kafka publish failed: {ex}") from ex

	def flush(self, timeout_seconds: float) -> None:
		remaining = self._producer.flush(timeout_seconds)
		if remaining:
			raise ProducerError(f"Kafka producer flush timed out with {remaining} message(s) remaining")
		if self._errors:
			raise ProducerError("; ".join(self._errors))

	def _delivery_callback(self, error, message) -> None:
		if error is not None:
			self._errors.append(str(error))
