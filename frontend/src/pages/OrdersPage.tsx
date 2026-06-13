export function OrdersPage() {
  return (
    <section className="page-stack" aria-labelledby="orders-title">
      <div className="page-heading">
        <p className="eyebrow">Trading</p>
        <h1 id="orders-title">Orders</h1>
      </div>
      <div className="empty-panel">
        <strong>No orders</strong>
        <span>Order history will appear here.</span>
      </div>
    </section>
  );
}
