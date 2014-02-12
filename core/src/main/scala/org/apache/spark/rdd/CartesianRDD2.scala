/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.rdd

import java.io.{ ObjectOutputStream, IOException }
import scala.reflect.ClassTag
import org.apache.spark._

private[spark] class CartesianRDD2[T: ClassTag](
  sc: SparkContext,
  var rdd1: RDD[T],
  var rdd2: RDD[T])
  extends RDD[Pair[T, T]](sc, Nil)
  with Serializable {

  val numPartitionsInRdd2 = rdd2.partitions.size

  override def getPartitions: Array[Partition] = {
    val array = new Array[Partition]((rdd1.partitions.size * (rdd2.partitions.size+1))/2)
    var idx : Int = 0;
    for (s1 <- rdd1.partitions) {
      for (s2 <- rdd2.partitions) {
        if (s1.index <= s2.index) {
          array(idx) = new CartesianPartition(idx, rdd1, rdd2, s1.index, s2.index)
          idx = idx + 1;
        }
      }
    }
    array
  }

  override def getPreferredLocations(split: Partition): Seq[String] = {
    val currSplit = split.asInstanceOf[CartesianPartition]
    (rdd1.preferredLocations(currSplit.s1) ++ rdd2.preferredLocations(currSplit.s2)).distinct
  }

  override def compute(split: Partition, context: TaskContext) = {
    val currSplit = split.asInstanceOf[CartesianPartition]
    for (
      x <- rdd1.iterator(currSplit.s1, context);
      y <- rdd2.iterator(currSplit.s2, context)
    ) yield (x, y)
  }

  override def getDependencies: Seq[Dependency[_]] = List(
    new NarrowDependency(rdd1) {
      def getParents(id: Int): Seq[Int] = List(id / numPartitionsInRdd2)
    },
    new NarrowDependency(rdd2) {
      def getParents(id: Int): Seq[Int] = List(id % numPartitionsInRdd2)
    })

  override def clearDependencies() {
    super.clearDependencies()
    rdd1 = null
    rdd2 = null
  }
}
