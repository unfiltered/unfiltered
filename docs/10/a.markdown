Trying Netty
------------

As with the Jetty server used for earlier console hacking, for Netty
we also need a starter project. If you don't have the `g8` command line
tool installed, please go [back to that page until you do][jetty].

[jetty]: Try+Unfiltered.html

### Enter the Console

This step will fetch a number of dependencies and sometimes certain
repositories are a little wonky, so cross your fingers.

    g8 n8han/unfiltered-netty --name=nettyplayin
    cd nettyplayin
    sbt console

Once you do get to a console, this should just work:

```scala
import unfiltered.request._
import unfiltered.response._
val hello = unfiltered.netty.cycle.Planify {
   case _ => ResponseString("hello world")
}
unfiltered.netty.Http(8080).plan(hello).run()
```

Direct a web browser to [http://127.0.0.1:8080/][local] and you'll
be in *hello world* business.

[local]: http://127.0.0.1:8080/
