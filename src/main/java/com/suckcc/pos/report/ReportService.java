package com.suckcc.pos.report;

import com.suckcc.pos.payment.PaymentMethod;
import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import com.suckcc.pos.shift.ShiftSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

	private final JdbcClient jdbc;
	private final ShiftService shiftService;

	public ReportService(JdbcClient jdbc, ShiftService shiftService) {
		this.jdbc = jdbc;
		this.shiftService = shiftService;
	}

	public record TenderRow(PaymentMethod method, long salesCents, long refundsCents, long netCents) {
	}

	public record TopProductRow(String sku, String name, long unitsSold, long grossCents) {
	}

	public record LowStockRow(Long id, String sku, String name, int stockQty) {
	}

	public record DailyReport(
			LocalDate date,
			long ticketCount,
			long grossSalesCents,
			long totalRefundsCents,
			long netSalesCents,
			List<TenderRow> byTender,
			List<TopProductRow> topProducts,
			List<LowStockRow> lowStock
	) {
	}

	public record ShiftReport(
			CashShift shift,
			ShiftSummary cashSummary,
			long ticketCount,
			long grossSalesCents,
			long totalRefundsCents,
			long netSalesCents,
			List<TenderRow> byTender
	) {
	}

	public DailyReport dailyReport(LocalDate date, int lowStockThreshold) {
		ZoneId zone = ZoneId.systemDefault();
		OffsetDateTime start = date.atStartOfDay(zone).toOffsetDateTime();
		OffsetDateTime end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

		long ticketCount = jdbc.sql("""
				SELECT COUNT(*)
				FROM sales
				WHERE status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				  AND completed_at >= :start AND completed_at < :end
				""")
				.param("start", start)
				.param("end", end)
				.query(Long.class)
				.single();

		long grossSales = jdbc.sql("""
				SELECT COALESCE(SUM(total_cents), 0)
				FROM sales
				WHERE status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				  AND completed_at >= :start AND completed_at < :end
				""")
				.param("start", start)
				.param("end", end)
				.query(Long.class)
				.single();

		long totalRefunds = jdbc.sql("""
				SELECT COALESCE(SUM(total_refund_cents), 0)
				FROM returns
				WHERE created_at >= :start AND created_at < :end
				""")
				.param("start", start)
				.param("end", end)
				.query(Long.class)
				.single();

		Map<PaymentMethod, Long> salesByMethod = queryMethodSums("""
				SELECT p.method, COALESCE(SUM(p.amount_cents), 0) AS total
				FROM payments p
				JOIN sales s ON s.id = p.sale_id
				WHERE s.status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				  AND s.completed_at >= :start AND s.completed_at < :end
				GROUP BY p.method
				""", start, end);

		Map<PaymentMethod, Long> refundsByMethod = queryMethodSums("""
				SELECT r.refund_method AS method, COALESCE(SUM(r.total_refund_cents), 0) AS total
				FROM returns r
				WHERE r.created_at >= :start AND r.created_at < :end
				GROUP BY r.refund_method
				""", start, end);

		List<TopProductRow> topProducts = jdbc.sql("""
				SELECT si.sku_snapshot AS sku,
				       si.product_name_snapshot AS name,
				       COALESCE(SUM(si.qty), 0) AS units_sold,
				       COALESCE(SUM(si.line_total_cents), 0) AS gross_cents
				FROM sale_items si
				JOIN sales s ON s.id = si.sale_id
				WHERE s.status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				  AND s.completed_at >= :start AND s.completed_at < :end
				GROUP BY si.sku_snapshot, si.product_name_snapshot
				ORDER BY units_sold DESC, gross_cents DESC
				LIMIT 10
				""")
				.param("start", start)
				.param("end", end)
				.query(TopProductRow.class)
				.list();

		List<LowStockRow> lowStock = jdbc.sql("""
				SELECT id, sku, name, stock_qty
				FROM products
				WHERE active = TRUE AND track_inventory = TRUE AND stock_qty <= :threshold
				ORDER BY stock_qty ASC, name ASC
				""")
				.param("threshold", lowStockThreshold)
				.query(LowStockRow.class)
				.list();

		return new DailyReport(
				date,
				ticketCount,
				grossSales,
				totalRefunds,
				grossSales - totalRefunds,
				buildTenderRows(salesByMethod, refundsByMethod),
				topProducts,
				lowStock
		);
	}

	public ShiftReport shiftReport(Long shiftId) {
		CashShift shift = shiftService.recentShifts().stream()
				.filter(s -> s.getId().equals(shiftId))
				.findFirst()
				.orElse(null);
		if (shift == null) {
			return null;
		}
		ShiftSummary cashSummary = shiftService.summarize(shift);

		long ticketCount = jdbc.sql("""
				SELECT COUNT(*)
				FROM sales
				WHERE shift_id = :shiftId AND status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				""")
				.param("shiftId", shiftId)
				.query(Long.class)
				.single();

		long grossSales = jdbc.sql("""
				SELECT COALESCE(SUM(total_cents), 0)
				FROM sales
				WHERE shift_id = :shiftId AND status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				""")
				.param("shiftId", shiftId)
				.query(Long.class)
				.single();

		long totalRefunds = jdbc.sql("""
				SELECT COALESCE(SUM(total_refund_cents), 0)
				FROM returns
				WHERE shift_id = :shiftId
				""")
				.param("shiftId", shiftId)
				.query(Long.class)
				.single();

		Map<PaymentMethod, Long> salesByMethod = queryShiftMethodSums("""
				SELECT p.method, COALESCE(SUM(p.amount_cents), 0) AS total
				FROM payments p
				JOIN sales s ON s.id = p.sale_id
				WHERE s.shift_id = :shiftId AND s.status IN ('PAID', 'PARTIAL_RETURN', 'RETURNED')
				GROUP BY p.method
				""", shiftId);

		Map<PaymentMethod, Long> refundsByMethod = queryShiftMethodSums("""
				SELECT r.refund_method AS method, COALESCE(SUM(r.total_refund_cents), 0) AS total
				FROM returns r
				WHERE r.shift_id = :shiftId
				GROUP BY r.refund_method
				""", shiftId);

		return new ShiftReport(
				shift,
				cashSummary,
				ticketCount,
				grossSales,
				totalRefunds,
				grossSales - totalRefunds,
				buildTenderRows(salesByMethod, refundsByMethod)
		);
	}

	private Map<PaymentMethod, Long> queryMethodSums(String sql, OffsetDateTime start, OffsetDateTime end) {
		Map<PaymentMethod, Long> map = new EnumMap<>(PaymentMethod.class);
		jdbc.sql(sql)
				.param("start", start)
				.param("end", end)
				.query((rs, rowNum) -> {
					map.put(PaymentMethod.valueOf(rs.getString("method")), rs.getLong("total"));
					return null;
				});
		return map;
	}

	private Map<PaymentMethod, Long> queryShiftMethodSums(String sql, Long shiftId) {
		Map<PaymentMethod, Long> map = new EnumMap<>(PaymentMethod.class);
		jdbc.sql(sql)
				.param("shiftId", shiftId)
				.query((rs, rowNum) -> {
					map.put(PaymentMethod.valueOf(rs.getString("method")), rs.getLong("total"));
					return null;
				});
		return map;
	}

	private static List<TenderRow> buildTenderRows(
			Map<PaymentMethod, Long> sales,
			Map<PaymentMethod, Long> refunds
	) {
		return java.util.Arrays.stream(PaymentMethod.values())
				.map(m -> {
					long s = sales.getOrDefault(m, 0L);
					long r = refunds.getOrDefault(m, 0L);
					return new TenderRow(m, s, r, s - r);
				})
				.toList();
	}
}
