package com.yee.bigdata.utils

package society{

  package professional{

    class Executive{
      private[professional] var workDetails = null
      private[society] var friends = null
      private[this] var secrets = null

      def help(another:Executive): Unit = {
        println(another.workDetails)
        println(secrets)
        println(workDetails)
        // println(another.secrets)
      }
    }

    class Assistant{
      def assist(anExec:Executive):Unit = {
        println(anExec.workDetails)
        println(anExec.friends)
      }
    }

  }

  package social{
    import MyCurrency._

    class Acquaintance{
      def socialize(person:professional.Executive): Unit = {
        println(person.friends)
        // println(person.workDetails)

        def comCurrency(myCurrency: MyCurrency): Boolean = {
          myCurrency == A
        }
      }
    }
  }

  object MyCurrency extends Enumeration{
    type MyCurrency = Value
    val A,B,C,D = Value
  }

  import MyCurrency._


}


class FineGrainedAccessControl {

}
