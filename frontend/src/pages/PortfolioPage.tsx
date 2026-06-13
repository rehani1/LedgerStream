export function PortfolioPage() {
  return (
    <section className="page-stack" aria-labelledby="portfolio-title">
      <div className="page-heading">
        <p className="eyebrow">Portfolio</p>
        <h1 id="portfolio-title">Positions</h1>
      </div>
      <div className="metric-grid" aria-label="Portfolio summary">
        <article className="metric-card">
          <span className="metric-label">Cash</span>
          <strong>—</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">Equity</span>
          <strong>—</strong>
        </article>
        <article className="metric-card">
          <span className="metric-label">P&L</span>
          <strong>—</strong>
        </article>
      </div>
    </section>
  );
}
