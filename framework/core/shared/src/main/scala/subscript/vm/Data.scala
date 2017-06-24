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

package subscript.vm

import subscript.vm.executor._
import subscript.vm.model.callgraph.CallGraphTreeNode
/*
Overview of formal and actual parameter use

|| Formal declaration   ||        Formal type              ||  Actual call          || Value of _p ||
||` p: P               `||`       FormalInputParameter[P] `||` expr                `||`  ActualValueParameter     (   expr) `||
||` p: P?              `||`      FormalOutputParameter[P] `||` varExpr?            `||` ActualOutputParameter     (varExpr, {=>varExpr=_) `||
||` p: P??             `||` FormalConstrainedParameter[P] `||` expr                `||`  ActualValueParameter     (   expr, {=>   expr=_) `||
||`                    `||`                               `||` varExpr?            `||` ActualOutputParameter     (varExpr, {=>varExpr=_) `||
||`                    `||`                               `||` varExpr if(c)?      `||` ActualConstrainedParameter(   expr, {=>   expr=_}, {_=>c}) `||
||`                    `||`                               `||` formalParam??       `||`    ActualAdaptingParameter(_formalParam) `||
||`                    `||`                               `||` formalParam if(c)?? `||`    ActualAdaptingParameter(_formalParam, {=>c}) `||

 */
trait Data // to make an empty class file so that ant will not get confused

trait FormalParameter[T<:Any] {
  def value: T
  def matches(aValue: T, doIsForcing: Boolean = isForcing): Boolean
  def isInput      : Boolean
  def isOutput     : Boolean
  def isForcing    : Boolean
  def isConstrained: Boolean
  var name: Symbol = null
}
trait FormalParameter_readWrite[T<:Any] extends FormalParameter[T] {
  def value_=(v: T)
}
trait       FormalInputParameter[T<:Any] extends FormalParameter[T] {
  def bindToFormalInputParameter
  def ~(n:Symbol) = {name=n; bindToFormalInputParameter; this}
}
trait      FormalOutputParameter[T<:Any] extends FormalParameter_readWrite[T] {
  def bindToFormalOutputParameter
  def ~?(n:Symbol) = {name=n; bindToFormalOutputParameter; this}
  var value: T
}
trait FormalConstrainedParameter[T<:Any] extends FormalParameter_readWrite[T] {
  def bindToFormalConstrainedParameter; 
  def ~??(n:Symbol) = {name=n; bindToFormalConstrainedParameter; this}
  var value: T
//  def ~?? = ActualAdaptingParameter(this)
//  def ~??(constraint: T=>Boolean) = ActualAdaptingParameter(this, constraint)
}
case class CommunicationParameter[T<:Any](n: Symbol) extends FormalParameter[T] {
  name = n // TBD: improve, now we have both n and name as fields
  
    // we need here the default value for R (false, 0, null or a "Unit")
    // for some strange reason, the following line would go wrong:
    //
    // var result: R = _
    //
    // A solution using a temporary class was found at
    // http://missingfaktor.blogspot.com/2011/08/emulating-cs-default-keyword-in-scala.html
    class Tmp {var default: T = _} 
    var default_T: T = (new Tmp).default
    // luckily we have the default value for type R now...

  var value         = default_T
  def matches(aValue: T, doIsForcing: Boolean) = true 
  def isInput       = !isForcing && ! isOutput
  var isOutput      = false  
  var isForcing     = false // var, not def!!!
  var isConstrained = false
}

trait ActualParameterTrait[T<:Any] extends FormalParameter[T] {
  def originalValue: T
  def transfer {}  
  def matches      : Boolean = matches(value)
}
abstract class ActualParameter[T<:Any] extends ActualParameterTrait[T] {
  var value=originalValue
}
trait ParameterTransferrerTrait[T<:Any] extends ActualParameterTrait[T] {
  def transferFunction: T=>Unit
  override def transfer {transferFunction.apply(value)}
}
case class   ActualValueParameter[T<:Any](originalValue:T) extends ActualParameter[T] 
  with FormalInputParameter      [T]
  with FormalConstrainedParameter[T] {
  def bindToFormalInputParameter {/*println("bindToFormalInputParameter: "+name)*/}
  def bindToFormalConstrainedParameter = {/*println("bindToFormalConstrainedParameter: "+name); */isForcing=true}
  def matches(aValue: T, doIsForcing: Boolean = isForcing) = 
    if (doIsForcing) {
      //println("matches: "+name + " " + aValue + " <==> "+originalValue)
      aValue==originalValue
    } 
    else {
      //println("matches: "+name + " not forcing; isForcing="+isForcing + " originalValue: "+originalValue)
      true 
    }
  def isInput       = !isForcing  
  def isOutput      = false  
  var isForcing     = false // var, not def!!!
  def isConstrained = false
  
  if (originalValue.isInstanceOf[ActualValueParameter[_]]) Thread.dumpStack
}

case class  ActualOutputParameter[T<:Any](originalValue:T, transferFunction: T=>Unit) extends ActualParameter[T] 
  with ParameterTransferrerTrait [T]
  with FormalOutputParameter     [T] 
  with FormalConstrainedParameter[T] {
  def bindToFormalOutputParameter      {}
  def bindToFormalConstrainedParameter {}
  def matches(aValue: T, doIsForcing: Boolean = isForcing) = true  
  def isInput       = false  
  def isOutput      = true  
  def isForcing     = false
  def isConstrained = false
}

case class ActualConstrainedParameter[T<:Any](originalValue:T, transferFunction: T=>Unit, constraint: T=>Boolean) extends ActualParameter[T] 
  with ParameterTransferrerTrait [T]
  with FormalConstrainedParameter[T] {
  def matches(aValue: T, doIsForcing: Boolean = isForcing) = constraint.apply(aValue)  
  def bindToFormalConstrainedParameter {}
  def isInput       = false  
  def isOutput      = false  
  def isForcing     = false
  def isConstrained = true
}
//object ActualAdaptingParameter {def apply[T<:Any](adaptee: FormalConstrainedParameter[T], constraint: T=>Boolean=null) = ActualAdaptingParameter(adaptee, constraint)}
// adapting parameters, as in script a(i:Int??) = b(i??)
case class ActualAdaptingParameter[T<:Any](adaptee: FormalConstrainedParameter[T], constraint: T=>Boolean=null) 
  extends ActualParameter        [T] 
  with ParameterTransferrerTrait [T]
  with FormalConstrainedParameter[T] {
  val rootAdaptee: ActualParameter[T] = adaptee match {case a:ActualAdaptingParameter[_]=>a.rootAdaptee case _ => adaptee.asInstanceOf[ActualParameter[T]]}
  def bindToFormalConstrainedParameter {isForcing=rootAdaptee.isForcing}
  val originalValue  = adaptee.value // val, not def !!
  value = originalValue
  def transferFunction: T=>Unit = {adaptee.value = _}
  def matches(aValue: T, doIsForcing: Boolean = isForcing) = (constraint==null||constraint.apply(aValue))&&
                            adaptee.matches(aValue, doIsForcing)
  def isInput       = adaptee.isInput && !isForcing
  def isOutput      = adaptee.isOutput&&constraint==null
  var isForcing     = adaptee.isForcing
  def isConstrained = adaptee.isConstrained || adaptee.isOutput && constraint!=null
}
case class LocalVariable[V](name: Symbol) {
  def at(implicit node: CallGraphTreeNode): VariableHolder[V] = node.getLocalVariableHolder(name)
}
case class VariableHolder[V](var value: V)
