package org.scriptonbasestar.cache.loader.jdbc;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Map;

/**
 * Tests for JdbcMapLoader with H2 embedded database.
 */
public class JdbcMapLoaderTest {

	private EmbeddedDatabase db;
	private JdbcTemplate jdbcTemplate;

	@Before
	public void setUp() {
		// Create embedded H2 database
		db = new EmbeddedDatabaseBuilder()
			.setType(EmbeddedDatabaseType.H2)
			.addScript("classpath:schema.sql")
			.addScript("classpath:data.sql")
			.build();

		jdbcTemplate = new JdbcTemplate(db);
	}

	@After
	public void tearDown() {
		if (db != null) {
			db.shutdown();
		}
	}

	// ========== Constructor Validation Tests ==========

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_NullJdbcTemplate() {
		new JdbcMapLoader<>(null, "SELECT * FROM products WHERE id = ?", (rs, rowNum) -> null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_NullLoadOneSql() {
		new JdbcMapLoader<>(jdbcTemplate, null, (rs, rowNum) -> null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_EmptyLoadOneSql() {
		new JdbcMapLoader<>(jdbcTemplate, "  ", (rs, rowNum) -> null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_NullRowMapper() {
		new JdbcMapLoader<Long, Product>(jdbcTemplate, "SELECT * FROM products WHERE id = ?", null);
	}

	// ========== LoadOne Tests ==========

	@Test
	public void testLoadOne_Success() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			this::mapProduct
		);

		Product product = loader.loadOne(1L);

		Assert.assertNotNull(product);
		Assert.assertEquals(Long.valueOf(1L), product.getId());
		Assert.assertEquals("Laptop", product.getName());
		Assert.assertEquals(1200.00, product.getPrice(), 0.01);
	}

	@Test(expected = SBCacheLoadFailException.class)
	public void testLoadOne_NotFound() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			this::mapProduct
		);

		// Key 999 does not exist
		loader.loadOne(999L);
	}

	@Test(expected = SBCacheLoadFailException.class)
	public void testLoadOne_NullKey() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			this::mapProduct
		);

		loader.loadOne(null);
	}

	@Test(expected = SBCacheLoadFailException.class)
	public void testLoadOne_InvalidSql() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM non_existent_table WHERE id = ?",
			this::mapProduct
		);

		loader.loadOne(1L);
	}

	// ========== LoadAll Tests ==========

	@Test
	public void testLoadAll_Success() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			"SELECT * FROM products",
			this::mapProduct,
			Product::getId
		);

		Map<Long, Product> allProducts = loader.loadAll();

		Assert.assertNotNull(allProducts);
		Assert.assertEquals(3, allProducts.size());
		Assert.assertTrue(allProducts.containsKey(1L));
		Assert.assertTrue(allProducts.containsKey(2L));
		Assert.assertTrue(allProducts.containsKey(3L));

		Product product1 = allProducts.get(1L);
		Assert.assertEquals("Laptop", product1.getName());

		Product product2 = allProducts.get(2L);
		Assert.assertEquals("Mouse", product2.getName());
	}

	@Test
	public void testLoadAll_WithCondition() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			"SELECT * FROM products WHERE price > 100",
			this::mapProduct,
			Product::getId
		);

		Map<Long, Product> expensiveProducts = loader.loadAll();

		Assert.assertNotNull(expensiveProducts);
		Assert.assertEquals(1, expensiveProducts.size());  // Only Laptop (price > 100)
		Assert.assertTrue(expensiveProducts.containsKey(1L));  // Laptop: 1200.00
		Assert.assertFalse(expensiveProducts.containsKey(2L));  // Mouse: 25.99 (< 100)
		Assert.assertFalse(expensiveProducts.containsKey(3L));  // Keyboard: 89.99 (< 100)
	}

	@Test
	public void testLoadAll_NoLoadAllSql() throws SBCacheLoadFailException {
		// Constructor without loadAllSql
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			this::mapProduct
		);

		Map<Long, Product> result = loader.loadAll();

		Assert.assertNotNull(result);
		Assert.assertTrue(result.isEmpty());
	}

	@Test(expected = SBCacheLoadFailException.class)
	public void testLoadAll_InvalidSql() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			"SELECT * FROM non_existent_table",
			this::mapProduct,
			Product::getId
		);

		loader.loadAll();
	}

	// ========== Integration Tests ==========

	@Test
	public void testIntegration_MultipleLoads() throws SBCacheLoadFailException {
		JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
			jdbcTemplate,
			"SELECT * FROM products WHERE id = ?",
			"SELECT * FROM products",
			this::mapProduct,
			Product::getId
		);

		// Load individual products
		Product product1 = loader.loadOne(1L);
		Product product2 = loader.loadOne(2L);

		Assert.assertEquals("Laptop", product1.getName());
		Assert.assertEquals("Mouse", product2.getName());

		// Load all
		Map<Long, Product> allProducts = loader.loadAll();
		Assert.assertEquals(3, allProducts.size());
	}

	// ========== Helper Methods ==========

	private Product mapProduct(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		Product product = new Product();
		product.setId(rs.getLong("id"));
		product.setName(rs.getString("name"));
		product.setPrice(rs.getDouble("price"));
		return product;
	}

	// ========== Test Model Class ==========

	public static class Product {
		private Long id;
		private String name;
		private double price;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public double getPrice() {
			return price;
		}

		public void setPrice(double price) {
			this.price = price;
		}
	}
}
