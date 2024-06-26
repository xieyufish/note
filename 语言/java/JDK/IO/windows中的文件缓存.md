# Windows中的文件缓存

默认情况下，Windows会将磁盘读取和写入到磁盘的文件内容数据存放到一个叫做系统缓存的地方缓存起来，后续的操作都针对这个系统缓存进行；在Windows中有一个缓存管理器来专门管理及操作这个缓存。用一个图来表示如下：

 ![dddd](.\images\fig3.png)

在系统缓存中的文件数据会每隔一定时间写入到磁盘中，被文件数据占用的内存也会被释放—这个操作就叫flush cache。在cache flush之前或者是数据写入文件的这个延迟策略就叫**延迟写**，这个操作是由缓存管理器每隔特定的时间间隔来触发的（这个时间间隔一般根据数据写入到缓存中的时间长度或者是最后一次读操作的时间来决定，这样可以确保经常被读取的文件数据可以尽量保留在系统缓存中）。

如上图所描述的，当用户进程发出一个读操作时，由缓存管理器将磁盘上一个256KB的块被读取到系统缓存中一个256KB大小的槽中，然后用户进程将这个槽拷贝到进程的地址空间中；然后用户进程处理文件数据之后，发出写操作，用户进程将修改过的数据写回到系统缓存中对应的槽中，直到缓存管理器确认这块数据短时间内不再需要的时候，由缓存管理器将这块数据写到磁盘上。

文件系统缓存能提高的I/O性能大小取决于所要操作的文件大小。比如当我们要读写一个非常大的文件的时候，系统缓存可能还会降低I/O效率（因为大文件读写，在你读取到后面的时候，前面读写的内容可能都已经写入到磁盘中去了，也就是说每个读写操作都涉及了一次磁盘读写，但是却并没有有效的利用到这个系统缓存带来好处，同理对于只用一次的文件内容，缓存的作用也不大），除了磁盘读写之外，还需要额外的从系统缓存拷贝到用户进程地址空间。

针对这种场景，我们可以在打开文件的时候关闭系统缓存功能。在通过***CreateFile***打开文件的时候，传递一个***FILE_FLAG_NO_BUFFERING***属性值参数来关闭系统缓存功能，这样所有对文件内容的读写操作都会直接操作磁盘，而不需要多一次系统缓存与用户进程之间的拷贝操作（但是，这种方式只是针对文件内容而言，对于文件的元数据属性依然会先缓存，这种情况的话可以通过函数***FlushFileBuffers***或者指定属性***FILE_FLAG_WRITE_THROUGH***来将缓存的元数据信息刷到磁盘中）。

从上面的描述，缓存管理器flush cache操作的频率对系统性能是有重大影响的。如果flush操作太频繁，系统性能下降可能会很严重，而如果flush操作很少，则系统内存会被这个缓存给耗尽或者是系统突然宕机导致缓存中的数据丢失。为了确保正确的刷新时机，缓存管理器每一秒会产生一个叫做**延迟写进程**，这个延迟写进程负责将最近的没有被flush到磁盘的数据，取1/8个内存页大小刷到磁盘；为了优化系统性能，这个进程会持续的评估写入磁盘的数据量来决定下次刷到磁盘的数据量大小。对于临时文件，只会存放在系统缓存中，并不会进行刷盘操作。

**FILE_FLAG_WRITE_THROUGH**

对于某些应用，可能需要将写操作的内容立即刷新到磁盘中（读操作依然可以从系统缓存读），针对这种情况Windows为***CreateFile***等函数提供了***FILE_FLAG_WRITE_THROUGH***属性，这个属性表示：数据除了写入到系统缓存，还会让缓存管理器立即将数据写入到磁盘，而不是等到延迟写的时机再写。

除了上面提到的***FILE_FLAG_NO_BUFFERING***和***FILE_FLAG_WRITE_THROUGH***属性外，Windows还提供了一个函数***FlushFileBuffers***，可以将系统缓存中的内容强制刷盘（将文件内容和文件元数据一起）。

文件的元数据是始终会被缓存的（不管设置什么属性），因此，为了将变动过的文件元数据存储到磁盘上，你必须使用***FlushFileBuffers***函数或者是指定***FILE_FLAG_WRITE_THROUGH***属性。