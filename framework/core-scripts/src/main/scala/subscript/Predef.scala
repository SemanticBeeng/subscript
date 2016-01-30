/*
    This file is part of Subscript - an extension of the Scala language 
                                     with constructs from Process Algebra.

    Subscript is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License and the 
    GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Subscript consists partly of a "virtual machine". This is a library; 
    Subscript applications may distribute this library under the 
    GNU Lesser General Public License, rather than under the 
    GNU General Public License. This way your applications need not 
    be made Open Source software, in case you don't want to.

    Subscript is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You may have received a copy of the GNU General Public License
    and the GNU Lesser General Public License along with Subscript.
    If not, see <http://www.gnu.org/licenses/>
*/

package subscript
import subscript.language

import scala.util.{Try,Success,Failure}

import subscript.vm._
import subscript.DSL._
import subscript.vm.executor._
import subscript.vm.model.template._
import subscript.vm.model.template.concrete._
import subscript.vm.model.callgraph._
import subscript.vm.model.callgraph.generic._

import subscript.objectalgebra._

// Predefined stuff - pass and some scripts: times, delta, epsilon, nu
//
object Predef extends CorePredefTrait {
  
  implicit script..
    process2script(p: SSProcess) = p.lifecycle

  script..
    times(n:Int) = while(pass<n)

    // alternative names would be: never, nothing, neutral
    // delta        = [-]
    // epsilon      = [+]
    // nu           = []

    sleep(t: Long) = {* Thread sleep t *}

    // FTTB, success and failure are here. Until there's a better way to set them.
    success(x  : Any      ) = {!x!}

    failure(msg: String   ): Any = {!throw new RuntimeException(msg)!}
    failure(t  : Throwable): Any = {!throw t!}

    
//    break_up(n:Int) = {!here.break_up(n)!}
//    break_up1 = break_up(1)
//    break_up2 = break_up(2)

}
