import os
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv


@dataclass(frozen=True)
class MarketDataSettings:
	kafka_bootstrap_servers: str
	kafka_security_protocol: Optional[str]
	kafka_sasl_mechanism: Optional[str]
	kafka_sasl_username: Optional[str]
	kafka_sasl_password: Optional[str]
	tick_topic: str
	replay_speed: float
	data_path: Path
	log_level: str
	dry_run: bool
	producer_flush_timeout_seconds: float

	@classmethod
	def from_env(cls) -> "MarketDataSettings":
		load_dotenv()
		return cls(
			kafka_bootstrap_servers=os.getenv("MARKET_DATA_KAFKA_BOOTSTRAP_SERVERS", "localhost:19092"),
			kafka_security_protocol=optional_env("MARKET_DATA_KAFKA_SECURITY_PROTOCOL"),
			kafka_sasl_mechanism=optional_env("MARKET_DATA_KAFKA_SASL_MECHANISM"),
			kafka_sasl_username=optional_env("MARKET_DATA_KAFKA_SASL_USERNAME"),
			kafka_sasl_password=optional_env("MARKET_DATA_KAFKA_SASL_PASSWORD"),
			tick_topic=os.getenv("MARKET_DATA_TICK_TOPIC", "market.tick"),
			replay_speed=float(os.getenv("MARKET_DATA_REPLAY_SPEED", "1.0")),
			data_path=Path(os.getenv("MARKET_DATA_FILE", "data/sample_ticks.csv")),
			log_level=os.getenv("MARKET_DATA_LOG_LEVEL", "INFO"),
			dry_run=os.getenv("MARKET_DATA_DRY_RUN", "false").lower() == "true",
			producer_flush_timeout_seconds=float(os.getenv("MARKET_DATA_PRODUCER_FLUSH_TIMEOUT_SECONDS", "10")),
		)


def optional_env(name: str) -> Optional[str]:
	value = os.getenv(name)
	if value is None or not value.strip():
		return None
	return value.strip()
