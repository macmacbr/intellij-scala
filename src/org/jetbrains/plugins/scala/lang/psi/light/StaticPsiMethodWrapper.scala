package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.Key
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import _root_.scala.collection.immutable.HashMap

/**
  * @author Alefas
  * @since 27.02.12
  */
class StaticPsiMethodWrapper private(val method: PsiMethod, containingClass: PsiClass)
  extends LightMethodAdapter(method.getManager, method, containingClass) with LightScalaMethod {
  setNavigationElement(method)

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "static" => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getModifierList: PsiModifierList = new LightModifierList(getManager, ScalaLanguage.INSTANCE) {
    override def hasModifierProperty(name: String): Boolean = {
      name match {
        case "static" => true
        case _ => super.hasModifierProperty(name)
      }
    }

    override def hasExplicitModifier(name: String): Boolean = {
      name match {
        case "static" => true
        case _ => super.hasModifierProperty(name)
      }
    }
  }

  override def isWritable: Boolean = getContainingFile.isWritable

  override protected def returnType: ScType = ???

  override protected def parameterListText: String = ???

  override lazy val getReturnType: PsiType = method.getReturnType

  override lazy val getReturnTypeElement: PsiTypeElement = method.getReturnTypeElement

  override lazy val getParameterList: PsiParameterList = method.getParameterList
}

object StaticPsiMethodWrapper {
  private val KEY: Key[HashMap[PsiClass, (StaticPsiMethodWrapper, Long)]] = Key.create("static.psi.method.wrapper.key")

  def getWrapper(method: PsiMethod, containingClass: PsiClass): StaticPsiMethodWrapper = {
    var data = method.getUserData(KEY)
    if (data == null) {
      data = new HashMap()
      method.putUserData(KEY, data)
    }
    val count = ScalaPsiManager.instance(method.getProject).getModificationCount
    var res = data.getOrElse(containingClass, null)
    if (res != null && res._2 == count) return res._1
    res = (new StaticPsiMethodWrapper(method, containingClass), count)
    data += ((containingClass, res))
    method.putUserData(KEY, data)
    res._1
  }
}
