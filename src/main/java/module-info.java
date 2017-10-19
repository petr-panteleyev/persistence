/**
 Persistence library provides annotation classes for database access.

 <p style="font-size: large;"><strong>Database</strong></p>

 <p>
 Database manipulation is beyond the scope of this API. Calling code must supply correct {@link javax.sql.DataSource}
 and ensure database does exist and proper access control is established.
 </p>

 <p style="font-size: large;"><strong>Table</strong></p>

 <p>
 Class implementing database table is defined by the annotation {@link org.panteleyev.persistence.annotations.Table}.
 Such class must also implement interface {@link org.panteleyev.persistence.Record} and at least method
 {@link org.panteleyev.persistence.Record#getId}.
 </p>

 <p>
 API currently supports only integer as primary key type. Use {@link org.panteleyev.persistence.DAO#generatePrimaryKey} to
 generate unique values for each table class. Also make sure that application calls
 {@link org.panteleyev.persistence.DAO#preload} first.
 </p>

 <p style="font-size: large;"><strong>Mutable Objects</strong></p>

 <p>Mutable objects must implement appropriate setters following JavaBean specification.</p>

  <pre><code>
{@literal @}Table("book")
class Book implements Record {
    private int id;
    private String title;

    {@literal @}Field(value = Field.ID, primaryKey = true)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    {@literal @}Field("title")
    public String getTitle() {
        return title
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
 </code></pre>

 <p style="font-size: large;"><strong>Immutable Objects</strong></p>

 <p>Immutable objects are supported by annotation {@link org.panteleyev.persistence.annotations.RecordBuilder}
 as shown below.</p>

 <pre><code>
{@literal @}Table("book")
class Book implements Record {
    private final int id;
    private final String title;

    {@literal @}RecordBuilder
    public Book ({@literal @}Field(Field.ID) int id, {@literal @}Field("title") String title) {
        this.id = id;
        this.title = title;
    }

    {@literal @}Field(value = Field.ID, primaryKey = true)
    public int getId() {
        return id;
    }

    {@literal @}Field("title")
    public String getTitle() {
        return title
    }
}
 </code></pre>

 <p style="font-size: large;"><strong>Data Types</strong></p>

 <p>The following data types are supported:</p>

 <table border="1">
 <caption></caption>
 <tr><th>Java</th><th>SQLite</th><th>MySQL</th><th>Comment</th></tr>
 <tr><td>int<br>{@link java.lang.Integer}</td><td>INTEGER</td><td>INTEGER</td><td></td></tr>
 <tr><td>long<br>{@link java.lang.Long}</td><td>INTEGER</td><td>BIGINT</td><td></td></tr>
 <tr><td>bool<br>{@link java.lang.Boolean}</td><td>BOOLEAN</td><td>BOOLEAN</td><td></td></tr>
 <tr>
 <td>{@link java.lang.String}</td>
 <td>VARCHAR ( {@link org.panteleyev.persistence.annotations.Field#length} )</td>
 <td>VARCHAR ( {@link org.panteleyev.persistence.annotations.Field#length} )</td>
 <td></td>
 </tr>
 <tr>
 <td>{@link java.math.BigDecimal}</td>
 <td>VARCHAR ( {@link org.panteleyev.persistence.annotations.Field#precision} + 1 )</td>
 <td>DECIMAL ( {@link org.panteleyev.persistence.annotations.Field#precision}, {@link org.panteleyev.persistence.annotations.Field#scale} )</td>
 <td>
 MySQL representation does not guarantee that retrieved value will be equal to original one by means of
 {@link java.lang.Object#equals}. Use {@link java.math.BigDecimal#compareTo} instead.
 </td>
 </tr>
 <tr>
 <td>{@link java.util.Date}</td>
 <td>INTEGER</td>
 <td>BIGINT</td>
 <td>Dates are stored as long using {@link java.util.Date#getTime}</td>
 </tr>
 <tr>
 <td>{@link java.time.LocalDate}</td>
 <td>INTEGER</td>
 <td>BIGINT</td>
 <td>Local dates are stored as long using {@link java.time.LocalDate#toEpochDay}</td>
 </tr>
 </table>

 <p style="font-size: large;"><strong>Indexes and Foreign Keys</strong></p>

 <pre><code>
{@literal @}Table("parent_table")
public class ParentTable implements Record {
    // ...

    private String data;

    {@literal @}Field("data")
    {@literal @}Index(value = "data", unique = true)
    public String getData() {
        return data;
    }
}
 </code></pre>

 <p>This will produce the following SQL for indexed field:<br><code>CREATE UNIQUE INDEX data ON parent_table(data)</code></p>

 <pre><code>
{@literal @}Table("child_table")
public class ChildTable implements Record {
    // ...

    {@literal @}Field("parent_data")
    {@literal @}ForeignKey(table = ParentTable.class, field = "data",
        onDelete = ReferenceOption.RESTRICT, onUpdate = ReferenceOption.CASCADE)
    public String getParentData() {
        return parentData;
    }
}
 </code></pre>

 <p>This will produce the following SQL for the foreign key:<br>
 <code>CREATE FOREIGN KEY(parent_data) REFERENCES parent_table(data) ON DELETE RESTRICT ON UPDATE CASCADE</code>
 </p>


 @see <a href="https://dev.mysql.com/doc/refman/5.7/en/data-types.html">MySQL Data Types</a>
 @see <a href="http://download.oracle.com/otn-pub/jcp/7224-javabeans-1.01-fr-spec-oth-JSpec/beans.101.pdf">Java Beans Specification</a>

 */
module org.panteleyev.persistence {
    requires java.base;
    requires java.sql;
    requires java.desktop;
    requires java.naming;

    exports org.panteleyev.persistence;
    exports org.panteleyev.persistence.annotations;
}