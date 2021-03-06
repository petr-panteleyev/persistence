# Change Log

## 19.2.0 - 2019-04-07

* MySQL: Option to store UUID as VARCHAR(36)
* Additional API methods using connection as parameter
* Unique option for @Column annotation
* Delete by id updated to support generic primary key
* Single get returns Optional (INCOMPATIBLE)

## 19.1.0 - 2019-03-23

* New annotation for primary key (INCOMPATIBLE)
* Primary key of type: Integer, Long, UUID, String
* UUID fields support
* JSON flavor of String field
* Explicit database type instead of auto-detection

## 18.2.0 - 2018-06-23

* byte[] support

## 18.1.1 - 2018-05-01

* Unsafe replaced with MethodHandle and VarHandle

## 18.1.0 - 2018-04-22

* Performance optimizations
* Java 10
* New annotations (INCOMPATIBLE)

## 3.2.1 - 2017-10-19

* Java 9 module

## 3.2.0 - 2017-10-07

* Truncate table
* Drop table
* Additional methods with connection as a parameter
* Utility methods in Record interface
* LocalDate fields support

## 3.1.0 - 2017-05-31

* Batch INSERT

### Fixed

* NPE when calling DAO.setDataSource(null)

## 3.0.0 - 2017-05-28

* MySQL support
* Database driver dependencies removed from pom.xml
* Support for indexes
* Foreign keys support fixed and fully tested
* Table listeners removed
* Updated documentation

## 2.2.1 - 2017-05-17

* Method to load database table directly to map

## 2.2.0 - 2017-02-18

* Enum fields support

## 2.1.0 - 2017-02-12

* Constants for Field annotation parameters

## 2.0.0 - 2017-02-04

* Immutable records, RecordBuilder annotation
* Support for fields of type Optional
* Internal caching removed

## 1.0.0 - 2017-01-08

* SQLite support
* Major annotations
* Mutable objects