-- -- Easy Questions
-- INSERT INTO question (question_text, option_a, option_b, option_c, option_d, correct_answer) 
-- VALUES 
-- ('What is a correct syntax to output "Hello World" in Java?', 'Console.WriteLine("Hello World");', 'System.out.println("Hello World");', 'print("Hello World");', 'echo("Hello World");', 'System.out.println("Hello World");'),
-- ('Which data type is used to create a variable that should store text?', 'string', 'String', 'txt', 'myString', 'String'),
-- ('How do you create a single line comment in Java?', '//', '/*', '#', '--', '//'),
-- ('Which operator is used to add together two values?', '.', '+', '&', '*', '+'),
-- ('What is the correct way to create an object called myObj of MyClass?', 'class MyClass = new myObj();', 'new myObj = MyClass();', 'MyClass myObj = new MyClass();', 'class myObj = new MyClass();', 'MyClass myObj = new MyClass();'),
-- ('Array indexes start with:', '-1', '0', '1', '2', '0'),
-- ('What is the correct way to declare an integer variable in Java?', 'num x;', 'x = int;', 'int x;', 'integer x;', 'int x;'),
-- ('Which method can be used to find the length of a string?', 'getSize()', 'len()', 'length()', 'size()', 'length()'),
-- ('What is the correct way to declare a constant variable in Java?', 'const', 'constant', 'final', 'static', 'final'),
-- ('How do you start writing a while loop in Java?', 'while x > y:', 'while(x > y)', 'x > y while {', 'while x > y {', 'while(x > y)'),

-- -- Medium Questions
-- ('What is the difference between JDK and JRE?', 'JDK is for development, JRE is for running', 'They are the same thing', 'JRE is for development, JDK is for running', 'Neither is used anymore', 'JDK is for development, JRE is for running'),
-- ('What is method overloading in Java?', 'Same method name with different parameters', 'Same method in parent and child', 'Different method names with same parameters', 'None of these', 'Same method name with different parameters'),
-- ('What is the purpose of the "break" statement in Java?', 'To exit a loop or switch', 'To skip one iteration', 'To continue execution', 'To throw an exception', 'To exit a loop or switch'),
-- ('What is a marker interface in Java?', 'Interface with no methods', 'Interface with one method', 'Interface with static methods', 'Interface with default methods', 'Interface with no methods'),
-- ('What is the difference between ArrayList and LinkedList?', 'No difference', 'ArrayList uses array, LinkedList uses double linked list', 'LinkedList is faster', 'ArrayList is always better', 'ArrayList uses array, LinkedList uses double linked list'),
-- ('What is the purpose of the "volatile" keyword?', 'Thread safety', 'Performance improvement', 'Memory management', 'Exception handling', 'Thread safety'),
-- ('What is the difference between "==" and ".equals()" in Java?', 'No difference', 'One for primitives, one for objects', 'One is faster', 'One is deprecated', 'One for primitives, one for objects'),
-- ('What is the purpose of the "synchronized" keyword?', 'Thread safety', 'Memory management', 'Exception handling', 'Performance improvement', 'Thread safety'),
-- ('What is a daemon thread in Java?', 'Background thread', 'Main thread', 'User thread', 'System thread', 'Background thread'),
-- ('What is the purpose of the "finally" block?', 'Always executes after try-catch', 'Optional block', 'Alternative to catch', 'Error handling', 'Always executes after try-catch'),

-- -- Hard Questions
-- ('Explain the Diamond Problem in Java:', 'Multiple inheritance issue in interfaces', 'Memory leak in collections', 'Thread deadlock situation', 'Stack overflow in recursion', 'Multiple inheritance issue in interfaces'),
-- ('What is the difference between Callable and Runnable?', 'Callable returns value and throws exception', 'They are the same', 'Runnable is newer', 'Callable is deprecated', 'Callable returns value and throws exception'),
-- ('What is a reentrant lock in Java?', 'Lock that can be acquired multiple times by same thread', 'One-time lock', 'System lock', 'Database lock', 'Lock that can be acquired multiple times by same thread'),
-- ('What is the purpose of weak references in Java?', 'Memory management optimization', 'Performance improvement', 'Exception handling', 'Thread safety', 'Memory management optimization'),
-- ('Explain the concept of memory leaks in Java:', 'Objects that are no longer used but not garbage collected', 'System running out of memory', 'CPU usage too high', 'Disk space full', 'Objects that are no longer used but not garbage collected'),
-- ('What is the difference between fail-fast and fail-safe iterators?', 'Fail-fast throws exception on modification during iteration', 'No difference', 'Fail-safe is newer', 'Fail-fast is deprecated', 'Fail-fast throws exception on modification during iteration'),
-- ('What is the purpose of the Phaser class in Java?', 'Synchronization barrier for dynamic number of threads', 'Database connection', 'File handling', 'Exception management', 'Synchronization barrier for dynamic number of threads'),
-- ('Explain the difference between shallow copy and deep copy:', 'Deep copy creates new objects for nested references', 'They are the same', 'Shallow copy is newer', 'Deep copy is deprecated', 'Deep copy creates new objects for nested references'),
-- ('What is the purpose of the sun.misc.Unsafe class?', 'Low-level system operations', 'GUI development', 'Database operations', 'Web services', 'Low-level system operations'),
-- ('What is the difference between G1 and CMS garbage collectors?', 'G1 is region-based, CMS is concurrent mark sweep', 'They are the same', 'G1 is older', 'CMS is newer', 'G1 is region-based, CMS is concurrent mark sweep'),

-- -- Advanced Java 8+ Features
-- ('What is the purpose of Optional in Java?', 'Null pointer handling', 'Performance improvement', 'Exception handling', 'Memory management', 'Null pointer handling'),
-- ('Explain method references in Java 8:', 'Shorthand notation for lambda expressions', 'Exception handling', 'Memory management', 'Thread safety', 'Shorthand notation for lambda expressions'),
-- ('What is the difference between map and flatMap?', 'flatMap handles nested streams', 'No difference', 'map is newer', 'flatMap is deprecated', 'flatMap handles nested streams'),
-- ('What are the benefits of the Stream API?', 'Functional style operations on collections', 'Database management', 'File handling', 'GUI development', 'Functional style operations on collections'),
-- ('What is a CompletableFuture?', 'Asynchronous computation builder', 'Database connection', 'File handler', 'Thread pool', 'Asynchronous computation builder'),

-- -- Spring Framework Questions
-- ('What is Dependency Injection?', 'Inversion of Control principle', 'Database connection', 'File handling', 'Exception management', 'Inversion of Control principle'),
-- ('What is the difference between @Component and @Bean?', '@Component for classes, @Bean for methods', 'No difference', '@Bean is newer', '@Component is deprecated', '@Component for classes, @Bean for methods'),
-- ('What is the purpose of @Autowired?', 'Automatic dependency injection', 'Exception handling', 'Database connection', 'File handling', 'Automatic dependency injection'),
-- ('Explain the Spring Bean lifecycle:', 'Instantiation, population, custom init, destruction', 'Simple creation and deletion', 'No specific lifecycle', 'Random process', 'Instantiation, population, custom init, destruction'),
-- ('What is the difference between @RequestMapping and @GetMapping?', '@GetMapping is specific to GET requests', 'No difference', '@RequestMapping is newer', '@GetMapping is deprecated', '@GetMapping is specific to GET requests'),

-- -- Design Patterns
-- ('What is the Singleton pattern?', 'Ensures single instance of class', 'Multiple instances', 'Database pattern', 'GUI pattern', 'Ensures single instance of class'),
-- ('Explain the Factory pattern:', 'Creates objects without exposing creation logic', 'Database management', 'File handling', 'Exception handling', 'Creates objects without exposing creation logic'),
-- ('What is the Observer pattern?', 'One-to-many dependency between objects', 'Database pattern', 'File pattern', 'GUI pattern', 'One-to-many dependency between objects'),
-- ('What is the Strategy pattern?', 'Family of algorithms made interchangeable', 'Database strategy', 'File handling', 'Exception handling', 'Family of algorithms made interchangeable'),
-- ('Explain the Builder pattern:', 'Constructs complex objects step by step', 'Database building', 'File handling', 'Exception handling', 'Constructs complex objects step by step'),

-- -- Concurrency and Multithreading
-- ('What is thread pooling?', 'Reuse of multiple threads', 'Database connection pool', 'File handling', 'Exception management', 'Reuse of multiple threads'),
-- ('Explain the producer-consumer problem:', 'Synchronization between threads sharing buffer', 'Database issue', 'File handling', 'Memory management', 'Synchronization between threads sharing buffer'),
-- ('What is a race condition?', 'Timing dependent code defect', 'Performance issue', 'Memory leak', 'Database error', 'Timing dependent code defect'),
-- ('What is the purpose of the CountDownLatch?', 'Synchronization aid that allows threads to wait', 'Database connection', 'File handling', 'Exception handling', 'Synchronization aid that allows threads to wait'),
-- ('Explain thread starvation:', 'Thread unable to gain regular access to shared resources', 'Memory issue', 'Database error', 'File handling error', 'Thread unable to gain regular access to shared resources'),

-- -- Java Memory Management
-- ('What is the purpose of the String pool?', 'Memory optimization for string literals', 'Database connection pool', 'Thread pool', 'Exception handling', 'Memory optimization for string literals'),
-- ('Explain the OutOfMemoryError:', 'JVM cannot allocate object due to no memory', 'Database error', 'File error', 'Thread error', 'JVM cannot allocate object due to no memory'),
-- ('What is garbage collection in Java?', 'Automatic memory management', 'File cleaning', 'Database cleanup', 'Thread cleanup', 'Automatic memory management'),
-- ('What is the difference between heap and stack memory?', 'Heap for objects, stack for primitives and references', 'No difference', 'Heap is newer', 'Stack is deprecated', 'Heap for objects, stack for primitives and references'),
-- ('What is memory fragmentation?', 'Scattered unused memory blocks', 'Database issue', 'File system issue', 'Thread issue', 'Scattered unused memory blocks');

-- -- -- Additional Easy Questions
-- -- INSERT INTO question (question_text, option_a, option_b, option_c, option_d, correct_answer) 
-- -- VALUES 
-- -- ('What is the default value of a boolean variable in Java?', 'true', 'false', 'null', '0', 'false'),
-- -- ('Which package is automatically imported in all Java files?', 'java.util', 'java.lang', 'java.io', 'java.net', 'java.lang'),
-- -- ('What is the correct way to create a String object?', 'String s = "text";', 'String s = new String();', 'Both A and B', 'None of these', 'Both A and B'),
-- -- ('Which loop is guaranteed to execute at least once?', 'for loop', 'while loop', 'do-while loop', 'foreach loop', 'do-while loop'),
-- -- ('What is the size of byte data type in Java?', '1 byte', '2 bytes', '4 bytes', '8 bytes', '1 byte'),

-- -- -- Additional Medium Questions
-- -- ('What is the difference between StringBuilder and StringBuffer?', 'StringBuilder is not thread-safe but faster', 'StringBuffer is not thread-safe but faster', 'They are exactly same', 'None of these', 'StringBuilder is not thread-safe but faster'),
-- -- ('What happens when a constructor is not defined in a class?', 'Compilation error', 'Runtime error', 'Default constructor is created', 'Class cannot be instantiated', 'Default constructor is created'),
-- -- ('What is the purpose of the "super" keyword?', 'Call parent class methods', 'Call child class methods', 'Create new instance', 'None of these', 'Call parent class methods'),
-- -- ('What is method overriding in Java?', 'Same method in parent and child', 'Different method in same class', 'Same method name with different parameters', 'None of these', 'Same method in parent and child'),
-- -- ('What is the difference between abstract class and interface?', 'Abstract class can have constructor', 'Interface can have constructor', 'Both can have constructor', 'None can have constructor', 'Abstract class can have constructor'),

-- -- -- Additional Hard Questions
-- -- ('What is the difference between ClassLoader and Class.forName()?', 'ClassLoader is lazy, forName is eager', 'ClassLoader is eager, forName is lazy', 'They are same', 'None of these', 'ClassLoader is lazy, forName is eager'),
-- -- ('Explain the concept of Java ClassLoader:', 'Loads class files into JVM', 'Compiles Java code', 'Executes main method', 'None of these', 'Loads class files into JVM'),
-- -- ('What is the purpose of JIT compiler?', 'Converts bytecode to native code at runtime', 'Compiles Java to bytecode', 'Optimizes memory', 'None of these', 'Converts bytecode to native code at runtime'),
-- -- ('What is the difference between error and exception?', 'Errors are not recoverable', 'Exceptions are not recoverable', 'They are same', 'None of these', 'Errors are not recoverable'),
-- -- ('What is the purpose of native keyword in Java?', 'Method implemented in another language', 'Pure Java method', 'Abstract method', 'None of these', 'Method implemented in another language'),

-- -- -- Additional Java 8+ Features
-- -- ('What is the purpose of default methods in interfaces?', 'Add new methods without breaking implementations', 'Improve performance', 'Better memory management', 'None of these', 'Add new methods without breaking implementations'),
-- -- ('What are the new features in Java 9 modules?', 'Better encapsulation and dependency management', 'Only performance improvements', 'Only security features', 'None of these', 'Better encapsulation and dependency management'),
-- -- ('What is the difference between Predicate and Function in Java 8?', 'Predicate returns boolean, Function returns any type', 'They are same', 'Predicate is faster', 'None of these', 'Predicate returns boolean, Function returns any type'),
-- -- ('What is the purpose of parallel streams?', 'Parallel processing of stream elements', 'Better memory management', 'Improved syntax', 'None of these', 'Parallel processing of stream elements'),
-- -- ('Explain the concept of method chaining in streams:', 'Multiple operations in single statement', 'Better performance', 'Improved memory usage', 'None of these', 'Multiple operations in single statement'),

-- -- -- Additional Spring Framework Questions
-- -- ('What is the difference between @Service and @Component?', 'Semantic difference for service layer', 'Completely different functionality', 'Performance difference', 'None of these', 'Semantic difference for service layer'),
-- -- ('What is Spring Boot AutoConfiguration?', 'Automatic configuration based on classpath', 'Manual configuration', 'No configuration needed', 'None of these', 'Automatic configuration based on classpath'),
-- -- ('What is the purpose of @RestController?', 'Combines @Controller and @ResponseBody', 'Only for security', 'Only for database', 'None of these', 'Combines @Controller and @ResponseBody'),
-- -- ('Explain Spring Boot Actuator:', 'Monitoring and managing application', 'Only for development', 'Only for testing', 'None of these', 'Monitoring and managing application'),
-- -- ('What is Spring AOP?', 'Aspect Oriented Programming implementation', 'Database management', 'Security feature', 'None of these', 'Aspect Oriented Programming implementation'),

-- -- -- Additional Design Patterns
-- -- ('What is the Adapter pattern?', 'Converts interface into another interface', 'Only for database', 'Only for GUI', 'None of these', 'Converts interface into another interface'),
-- -- ('What is the Decorator pattern?', 'Adds behavior to object dynamically', 'Static behavior only', 'Only for database', 'None of these', 'Adds behavior to object dynamically'),
-- -- ('Explain the Command pattern:', 'Encapsulates request as an object', 'Only for database', 'Only for GUI', 'None of these', 'Encapsulates request as an object'),
-- -- ('What is the Proxy pattern?', 'Controls access to another object', 'Only for database', 'Only for security', 'None of these', 'Controls access to another object'),
-- -- ('What is the Template Method pattern?', 'Defines skeleton of algorithm in base class', 'Only for database', 'Only for GUI', 'None of these', 'Defines skeleton of algorithm in base class'),

-- -- -- Additional Concurrency and Multithreading
-- -- ('What is the difference between wait() and sleep()?', 'wait releases lock, sleep keeps lock', 'They are same', 'wait is faster', 'None of these', 'wait releases lock, sleep keeps lock'),
-- -- ('What is thread deadlock?', 'Circular waiting for resources', 'Program termination', 'Normal execution', 'None of these', 'Circular waiting for resources'),
-- -- ('Explain the Fork/Join Framework:', 'Parallel processing framework', 'Only for GUI', 'Only for database', 'None of these', 'Parallel processing framework'),
-- -- ('What is the purpose of the Semaphore class?', 'Controls access to limited resources', 'Only for GUI', 'Only for database', 'None of these', 'Controls access to limited resources'),
-- -- ('What is the difference between submit() and execute() in ExecutorService?', 'submit returns Future, execute doesnt', 'They are same', 'execute is faster', 'None of these', 'submit returns Future, execute doesnt'),

-- -- -- Additional Java Memory Management
-- -- ('What is the difference between soft and weak references?', 'Soft cleared only when memory needed', 'They are same', 'Weak is better', 'None of these', 'Soft cleared only when memory needed'),
-- -- ('What is the purpose of System.gc()?', 'Suggests garbage collection', 'Forces garbage collection', 'Prevents garbage collection', 'None of these', 'Suggests garbage collection'),
-- -- ('Explain the concept of memory leaks in Java:', 'Objects retained longer than needed', 'Normal memory usage', 'Efficient memory usage', 'None of these', 'Objects retained longer than needed'),
-- -- ('What is the purpose of finalize() method?', 'Cleanup before garbage collection', 'Regular method execution', 'Program termination', 'None of these', 'Cleanup before garbage collection'),
-- -- ('What is the difference between Stack and Heap in JVM?', 'Stack for method calls, Heap for objects', 'They are same', 'Stack is faster', 'None of these', 'Stack for method calls, Heap for objects'); 