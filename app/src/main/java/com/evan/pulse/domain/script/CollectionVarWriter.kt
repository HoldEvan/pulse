package com.evan.pulse.domain.script

import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.di.ServiceLocator

/**
 * 脚本对文件夹变量的写入。
 *
 * 只覆盖当前文件夹自身的变量（不含从父级继承来的），
 * 与 Postman 里 collectionVariables 的「写回所属集合」语义一致。
 */
class CollectionVarWriter(private val collectionId: Long) : ScriptEnvWriter {

    override fun replaceActiveVars(variables: List<Pair<String, String>>) {
        ServiceLocator.collectionStore.setVars(
            collectionId,
            variables.map { (key, value) -> KeyValueEntry(key, value) }
        )
    }

    override fun removeActiveVar(name: String) {
        ServiceLocator.collectionStore.setVars(
            collectionId,
            ServiceLocator.collectionStore.varsOf(collectionId).filterNot { it.key == name }
        )
    }
}
