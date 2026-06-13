export function RiskPage() {
  return (
    <section className="page-stack" aria-labelledby="risk-title">
      <div className="page-heading">
        <p className="eyebrow">Risk</p>
        <h1 id="risk-title">Exposure</h1>
      </div>
      <div className="metric-grid" aria-label="Risk summary">
        <article className="metric-card">
          <span className="metric-label">Gross exposure</span>
          <strong>—</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Largest position</span>
          <strong>—</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Unrealized P&L</span>
          <strong>—</strong>
        </article>
      </div>
    </section>
  );
}
