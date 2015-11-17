package org.nageler.sqlconnector.dbconn;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.nageler.sqlconnector.annotation.SqlField;
import org.nageler.sqlconnector.annotation.SqlReadOnly;
import org.nageler.sqlconnector.annotation.SqlTable;

import lombok.NonNull;
import lombok.extern.java.Log;

@Log
public class DBConn {
	Connection conn;

	Map<Class<?>, PreparedStatement> insertStatements = new HashMap<>();
	Map<Class<?>, Map<String, PreparedStatement>> selectStatements = new HashMap<>();
	
	public DBConn(@NonNull Connection conn) {
		this.conn = conn;
	}
	
	public <T> List<T> selectAll(Class<T> clazz) throws SQLException {
		return select(clazz, "");
	}
	
	public <T> List<T> select(Class<T> clazz, @NonNull String sqlSuffix, Object... parameters) throws SQLException {
		PreparedStatement stm = getSelectStatement(clazz, sqlSuffix);
		{
			int i = 1;
			for (Object param : parameters) {
				setPreparedStatmentParameter(stm, i, param);
			}
		}
		
		log.finer(stm.toString());
		
		ResultSet rs = stm.executeQuery();
		List<T> result = new LinkedList<>();
		List<Field> fields = getSqlFields(clazz);
		
		try {
			while (rs.next()) {
				T t = clazz.newInstance();
				
				int i = 1;
				for (Field f : fields) {
					readFromResultSet(f, rs, t, i);
					i++;
				}
				
				result.add(t);
			}
		} catch (InstantiationException e) {
			throw new IllegalArgumentException(String.format("failed to call %s.class.newInstance()", clazz.getName()), e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(String.format("failed to call %s.class.newInstance()", clazz.getName()), e);
		}
		
		return result;
	}

	public <T> void write(@NonNull T t) throws IllegalArgumentException, SQLException {
		Class<?> clazz = t.getClass();
		
		PreparedStatement stm = getInsertStatement(clazz);
		
		try {
			int i = 1;
			for (Field f : getSqlFields(clazz)) {
				if (!f.isAnnotationPresent(SqlReadOnly.class)) {
					setPreparedStatmentParameter(stm, i, f.get(t));
					i++;
				}
			}
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("could not get parameter", e);
		}
		
		stm.executeUpdate();
	}

	private PreparedStatement getInsertStatement(Class<?> clazz) throws SQLException {
		PreparedStatement stm;
		if ((stm = insertStatements.get(clazz)) != null) {
			return stm;
		}
		
		List<Field> fields = getSqlFields(clazz);
		
		String table = clazz.getAnnotation(SqlTable.class).value();
		StringJoiner columns = new StringJoiner(", ");
		StringJoiner values = new StringJoiner(", ");
		
		for (Field f : fields) {
			if (!f.isAnnotationPresent(SqlReadOnly.class)) {
				String name = f.getAnnotation(SqlField.class).column();
				columns.add("".equals(name) ? f.getName() : name);
				values.add("?");
			}
		}
		String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, columns, values);
		stm = conn.prepareStatement(sql);
		insertStatements.put(clazz, stm);
		return stm;
	}
	private PreparedStatement getSelectStatement(Class<?> clazz, String sqlSuffix) throws SQLException {
		Map<String, PreparedStatement> stms;
		PreparedStatement stm;
		if ((stms = selectStatements.get(clazz)) != null) {
			if ((stm = stms.get(sqlSuffix)) != null)
				return stm;
		} else {
			stms = new HashMap<>();
			selectStatements.put(clazz, stms);
		}
		
		log.fine(String.format("creating statement for %s (\"%s\")", clazz, sqlSuffix));
		
		List<Field> fields = getSqlFields(clazz);
		
		String table = clazz.getAnnotation(SqlTable.class).value();
		StringJoiner columns = new StringJoiner(", ");
		
		for (Field f : fields) {
			String name = f.getAnnotation(SqlField.class).column();
			columns.add("".equals(name) ? f.getName() : name);
		}
		String sql = String.format("SELECT %s FROM %s %s", columns, table, sqlSuffix);
		stm = conn.prepareStatement(sql);
		
		stms.put(sqlSuffix, stm);
		return stm;
	}
	
	private static List<Field> getSqlFields(Class<?> clazz) {
		List<Field> fields = new LinkedList<>();
		
		if (clazz.isAnnotationPresent(SqlTable.class)) {
			for (Field f : clazz.getFields()) {
				if (f.isAnnotationPresent(SqlField.class)) {
					fields.add(f);
				}
			}
		} else {
			throw new IllegalArgumentException("Class must have @SqlTable annotation");
		}
		return fields;
	}
	private static void setPreparedStatmentParameter(PreparedStatement stm, int i, Object o) throws SQLException {
		Class<?> type = o.getClass();
		
		if (Integer.class.isAssignableFrom(type))
			stm.setInt(i, (int) o);
		else if (Float.class.isAssignableFrom(type))
			stm.setFloat(i, (float) o);
		else if (Double.class.isAssignableFrom(type))
			stm.setDouble(i, (double) o);
		else if (Boolean.class.isAssignableFrom(type))
			stm.setBoolean(i, (boolean) o);
		else if (String.class.isAssignableFrom(type))
			stm.setString(i, (String) o);
		else if (Timestamp.class.isAssignableFrom(type))
			stm.setTimestamp(i, (Timestamp) o);
		else if (Date.class.isAssignableFrom(type))
			stm.setDate(i, (Date) o);
		else
			throw new IllegalArgumentException(String.format("%s is not supported", type));
	}
	private static void readFromResultSet(Field f, ResultSet rs, Object o, int i) throws IllegalArgumentException, IllegalAccessException, SQLException {
		Class<?> type = f.getType();
		
		if (type == int.class)
			f.setInt(o, rs.getInt(i));
		else if (type == float.class)
			f.setFloat(o, rs.getFloat(i));
		else if (type == double.class)
			f.setDouble(o, rs.getDouble(i));
		else if (type == boolean.class)
			f.setBoolean(o, rs.getBoolean(i));
		else if (Boolean.class.isAssignableFrom(type))
			f.set(o, rs.getBoolean(i));
		else if (Number.class.isAssignableFrom(type))
			f.set(o, rs.getObject(i));
		else if (String.class.isAssignableFrom(type))
			f.set(o, rs.getString(i));
		else if (Timestamp.class.isAssignableFrom(type))
			f.set(o, rs.getTimestamp(i));
		else if (Date.class.isAssignableFrom(type))
			f.set(o, rs.getDate(i));
		else
			throw new IllegalArgumentException(String.format("%s is not supported", type));
	}
	
}
