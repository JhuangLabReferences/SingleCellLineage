package main.scala

import java.util

import _root_.utils.{CutSites}
import main.scala.stats.StatsOutput
import utils.Utils

import scala.collection.mutable
import scala.io._
import java.io._
import scala.collection.mutable._
import scala.main.{ReverseReadOrientation, ForwardReadOrientation, SequencingRead}
import scala.sys.process._
import java.util.zip._

/**
 * created by aaronmck on 2/13/14
 *
 * Copyright (c) 2014, aaronmck
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 *
 */
case class Config(inputFileReads1: File = new File(UMIProcessing.NOTAREALFILENAME),
                  inputFileReads2: File = new File(UMIProcessing.NOTAREALFILENAME),
                  outputFastq1: File = new File(UMIProcessing.NOTAREALFILENAME),
                  outputFastq2: File = new File(UMIProcessing.NOTAREALFILENAME),
                  outputStats: File = new File(UMIProcessing.NOTAREALFILENAME),
                  outputUMIStats: File = new File(UMIProcessing.NOTAREALFILENAME),
                  outputAlignments: File = new File(UMIProcessing.NOTAREALFILENAME),
                  cutSites: File = new File(UMIProcessing.NOTAREALFILENAME),
                  umiLength: Int = 1,
                  umiStartPos: Int = 0,
                  primersEachEnd: File = new File(UMIProcessing.NOTAREALFILENAME),
                  reference: File = new File(UMIProcessing.NOTAREALFILENAME),
                  samplename: String = "TEST",
                  minimumUMIReads: Int = 5,
                  minimumSurvivingUMIReads: Int = 2)



object UMIProcessing extends App {
  val NOTAREALFILENAME = "/0192348102jr10234712930h8j19p0hjf129-348h512935"
  // please don't make a file with this name
  val NOTAREALFILE = new File(NOTAREALFILENAME)

  // parse the command line arguments
  val parser = new scopt.OptionParser[Config]("UMIMerge") {
    head("UMIMerge", "1.0")

    // *********************************** Inputs *******************************************************
    opt[File]("inputFileReads1") required() valueName ("<file>") action { (x, c) => c.copy(inputFileReads1 = x) } text ("first read file ")
    opt[File]("inputFileReads2") required() valueName ("<file>") action { (x, c) => c.copy(inputFileReads2 = x) } text ("second reads file")
    opt[File]("outputFastq1") required() valueName ("<file>") action { (x, c) => c.copy(outputFastq1 = x) } text ("the output stats file")
    opt[File]("outputFastq2") required() valueName ("<file>") action { (x, c) => c.copy(outputFastq2 = x) } text ("the output stats file")
    opt[File]("outputStats") required() valueName ("<file>") action { (x, c) => c.copy(outputStats = x) } text ("the output stats file")
    opt[File]("outputUMIStats") required() valueName ("<file>") action { (x, c) => c.copy(outputUMIStats = x) } text ("the output stats file")
    opt[File]("cutSites") required() valueName ("<file>") action { (x, c) => c.copy(cutSites = x) } text ("the location of the cutsites")
    opt[File]("primersEachEnd") required() valueName ("<file>") action { (x, c) => c.copy(primersEachEnd = x) } text ("the file containing the amplicon primers requred to be present, one per line, two lines total")
    opt[Int]("minimumUMIReads") action { (x, c) => c.copy(minimumUMIReads = x) } text ("the minimum number of reads that each UMI should have to be considered signal and not noise")
    opt[Int]("minimumSurvivingUMIReads") action { (x, c) => c.copy(minimumSurvivingUMIReads = x) } text ("the minimum number of reads that each UMI should have post filtering")

    opt[File]("reference") required() action { (x, c) => c.copy(reference = x) } text ("the reference (as a fasta)")
    opt[Int]("umiStart") required() action { (x, c) => c.copy(umiStartPos = x) } text ("the start position, zero based, of our UMIs")
    opt[Int]("umiLength") required() action { (x, c) => c.copy(umiLength = x) } text ("the length of our UMIs")
    opt[String]("samplename") required() action { (x, c) => c.copy(samplename = x) } text ("the sample name of this run")


    // some general command-line setup stuff
    note("processes reads with UMIs into merged reads\n")
    help("help") text ("prints the usage information you see here")
  }

  // *********************************** Run *******************************************************
  // run the actual read processing -- our argument parser found all of the parameters it needed
  parser.parse(args, Config()) map {
    config: Config => {
      umiAnalysis(config)
    }
  } getOrElse {
    println("Unable to parse the command line arguments you passed in, please check that your parameters are correct")
  }



  /**
   * given UMIed reads, process per UMI, merging reads and calling events
   * @param config our config object
   */
  def umiAnalysis(config: Config): Unit = {
    // our output files
    val outputFastq1File = new PrintWriter(config.outputFastq1)
    val outputFastq2File = new PrintWriter(config.outputFastq2)
    val cutsSiteObj = CutSites.fromFile(config.cutSites, 3)
    val outputStatsFile = new StatsOutput(config.outputStats,cutsSiteObj.size)

    // get the reference as a string
    var referenceString = ""
    Source.fromFile(config.reference).getLines().foreach { line => if (!line.startsWith(">")) referenceString += line }

    // setup clustered input of the fastq files
    // ------------------------------------------------------------------------------------------
    val forwardReads = Source.fromInputStream(Utils.gis(config.inputFileReads1.getAbsolutePath)).getLines().grouped(4)
    val reverseReads = Source.fromInputStream(Utils.gis(config.inputFileReads2.getAbsolutePath)).getLines().grouped(4)

    val primers = Source.fromFile(config.primersEachEnd.getAbsolutePath).getLines().map { line => line }.toList
    if (primers.length != 2)
      throw new IllegalStateException("You should only provide a primer file with two primers")

    // our containers for forward and reverse reads
    var umiReadsFWD = new mutable.HashMap[String, ArrayBuffer[SequencingRead]]()
    var umiReadsRVS = new mutable.HashMap[String, ArrayBuffer[SequencingRead]]()

    // our containers for forward and reverse reads
    val umiReadsFWDCount = new mutable.HashMap[String, Int]()
    val umiReadsFWDNoPrim = new mutable.HashMap[String, Int]()
    val umiReadsRVSCount = new mutable.HashMap[String, Int]()
    val umiReadsRVSNoPrim = new mutable.HashMap[String, Int]()
    val umisTotal = new mutable.HashMap[String, Boolean]()

    // --------------------------------------------------------------------------------
    // process the reads into bins of UMIs, keep fwd/rev reads together
    // --------------------------------------------------------------------------------
    print("Reading in sequences and parsing out UMIs (one dot per 10K reads):")
    var readsProcessed = 0
    forwardReads foreach { fGroup => {
      val rGroup = reverseReads.next()

      val umi = fGroup(1).slice(0, config.umiLength)
      val readNoUMI = fGroup(1).slice(config.umiLength, fGroup(1).length)
      val qualNoUMI = fGroup(3).slice(config.umiLength, fGroup(3).length)

      umisTotal(umi) = true

      val containsForward = readNoUMI contains (primers(0))
      val containsReverse = rGroup(1) contains (Utils.reverseComplement(primers(1)))

      if (containsForward && containsReverse) {
        val readBuilderF = umiReadsFWD.getOrElse(umi, new ArrayBuffer[SequencingRead]())
        val readBuilderR = umiReadsRVS.getOrElse(umi, new ArrayBuffer[SequencingRead]())

        readBuilderF += SequencingRead(fGroup(0), readNoUMI, qualNoUMI, ForwardReadOrientation, umi)
        umiReadsFWD(umi) = readBuilderF

        readBuilderR += SequencingRead(rGroup(0), rGroup(1), rGroup(3), ReverseReadOrientation, umi)
        umiReadsRVS(umi) = readBuilderR

        umiReadsFWDCount(umi) = umiReadsFWDCount.getOrElse(umi, 0) + 1
        umiReadsRVSCount(umi) = umiReadsRVSCount.getOrElse(umi, 0) + 1
      } else {
        umiReadsFWDNoPrim(umi) = umiReadsFWDNoPrim.getOrElse(umi, 0) + 1
        umiReadsRVSNoPrim(umi) = umiReadsRVSNoPrim.getOrElse(umi, 0) + 1
      }

      readsProcessed += 1
      if (readsProcessed % 10000 == 0)
        print(".")

    }
    }

    val umiStats = new PrintWriter(config.outputUMIStats)
    umiStats.write("umi\tforward.reads.with.adapter\treverse.reads.with.adapter\tforward.reads.without.adapter\treverse.reads.without.adapter\n")
    umisTotal.foreach { case (umi, exists) => umiStats.write(umi + "\t" + umiReadsFWDCount.getOrElse(umi, 0) + "\t" + umiReadsRVSCount.getOrElse(umi, 0) + "\t" +
      umiReadsFWDNoPrim.getOrElse(umi, 0) + "\t" + umiReadsRVSNoPrim.getOrElse(umi, 0) + "\n")
    }
    umiStats.close()

    // --------------------------------------------------------------------------------
    // for each UMI combination, downsample to the best X reads, and use those during
    // the merging process.  This helps us both in computational time as well as
    // removing bad reads that will just mess with us later
    // --------------------------------------------------------------------------------
    val umiReadsFWDReplacements = new mutable.HashMap[String, Array[SequencingRead]]()
    val umiReadsRVSReplacements = new mutable.HashMap[String, Array[SequencingRead]]()
    val umiReadsFWDReplacementsCounts = new mutable.HashMap[String, Int]()
    val umiReadsRVSReplacementsCounts = new mutable.HashMap[String, Int]()

    val downsampleSize = 40
    val minReads = 5
    var tooFewReadsUMI = 0
    var downsampledUMI = 0
    var justRightUMI = 0

    umiReadsFWD foreach { case (umi, fReadsBuilder) => {
      val fReads = fReadsBuilder.toArray
      val rReads = umiReadsRVS(umi).toArray

      umiReadsFWDReplacementsCounts(umi) = fReads.size
      umiReadsRVSReplacementsCounts(umi) = rReads.size

      if (fReads.size > downsampleSize) {
        downsampledUMI += 1
        umiReadsFWDReplacements(umi) = Downsampler.downsample(fReads, downsampleSize)
        //println("downsampled forward UMI " + umi + " from " + fReads.size + " to " + umiReadsFWDReplacements(umi).size)
      } else if (fReads.size > minReads) {
        umiReadsFWDReplacements(umi) = fReads
        justRightUMI += 1
      } else {
        tooFewReadsUMI += 1
      }


      if (rReads.size > downsampleSize) {
        umiReadsRVSReplacements(umi) = Downsampler.downsample(rReads, downsampleSize)
        //println("downsampled reverse UMI " + umi + " from " + fReads.size + " to " + umiReadsFWDReplacements(umi).size)
      } else if (rReads.size > minReads) {
        umiReadsRVSReplacements(umi) = rReads
      }
    }
    }
    println("Downsampled UMIs to a maximum of " + downsampleSize + " reads")
    println("Downsampled UMIs:" + downsampledUMI)
    println("To few reads UMIs:" + tooFewReadsUMI)
    println("UMIs kept as is:" + justRightUMI)


    var passingUMI = 0
    var totalWithUMI = 0

    var index = 0


    // --------------------------------------------------------------------------------
    // for each UMI -- process the collection of reads
    // --------------------------------------------------------------------------------
    umiReadsFWDReplacements.foreach { case (umi, reads) => {
      //println(umi)
      val reverseReads = umiReadsRVSReplacements(umi)
      val forwardReads = umiReadsFWDReplacements(umi)

      if (forwardReads.length > config.minimumUMIReads && reverseReads.length > config.minimumUMIReads) {
        val res = UMIMerger.mergeTogether(umi,
          forwardReads,
          reverseReads,
          umiReadsFWDReplacementsCounts(umi),
          umiReadsRVSReplacementsCounts(umi),
          cutsSiteObj,
          outputFastq1File,
          outputFastq2File,
          outputStatsFile,
          referenceString,
          config.reference,
          primers,
          config.samplename,
          config.minimumSurvivingUMIReads,
          cutsSiteObj)

        passingUMI += res
      }
      index += 1
      if (index % 10 == 0) {
        println("INFO: Processed " + index + " umis so far")
      }
    }
    }

    println("total UMIs passed:\t" + passingUMI + "\ntotal UMIs considered:\t" + umiReadsFWD.size)

    outputFastq1File.close()
    outputFastq2File.close()
    outputStatsFile.close()
  }
}
