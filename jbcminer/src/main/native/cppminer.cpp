#include "com_infine_demo_bcminer_cpp_CppMiner.h"

JNIEXPORT jlong JNICALL Java_com_infine_demo_bcminer_cpp_CppMiner_getTotalHashes (JNIEnv*, jobject) 
{
	return 42;
}

JNIEXPORT void JNICALL Java_com_infine_demo_bcminer_cpp_CppMiner_mine(JNIEnv*, jobject, jobject dataBuffer, jint startNonce, jobject resultBuffer)
{
	// TODO ... or not
}