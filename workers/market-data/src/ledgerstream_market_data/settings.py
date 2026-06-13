import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


@dataclass(frozen=True)
class MarketDataSettings:
	kafka_bootstrap_servers: str
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
			tick_topic=os.getenv("MARKET_DATA_TICK_TOPIC", "market.tick"),
			replay_speed=float(os.getenv("MARKET_DATA_REPLAY_SPEED", "1.0")),
			data_path=Path(os.getenv("MARKET_DATA_FILE", "data/sample_ticks.csv")),
			log_level=os.getenv("MARKET_DATA_LOG_LEVEL", "INFO"),
			dry_run=os.getenv("MARKET_DATA_DRY_RUN", "false").lower() == "true",
			producer_flush_timeout_seconds=float(os.getenv("MARKET_DATA_PRODUCER_FLUSH_TIMEOUT_SECONDS", "10")),
		)
