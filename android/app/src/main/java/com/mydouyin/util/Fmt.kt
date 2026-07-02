package com.mydouyin.util

/** 12300 -> "1.2万", 9 -> "9". Matches how Douyin shows counts. */
fun fmtCount(n: Int): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 1_0000_0000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0)
    else -> n.toString()
}
