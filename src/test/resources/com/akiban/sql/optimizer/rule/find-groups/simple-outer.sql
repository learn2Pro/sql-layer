SELECT * FROM customers LEFT JOIN orders ON customers.cid = orders.cid LEFT JOIN items ON orders.oid = items.oid
