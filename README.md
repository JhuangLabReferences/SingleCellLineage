# Single-cell GESTALT pipeline

This pipeline processes 2nd-generation sequencing of GESTALT lineage tracing barcodes. You can either setup the tool manually (following the directions in the Docker file), or use the Docker installation option. The pipeline merges reads by UMI, aligned the resulting consensus to a reference file, and aggregates results into plots describing the overall editing, as well as trees describing the relative lineage relationships within your data. *This code is a moving target, and may contain buggy implementations or changing code*. I will try to keep individual releases as stable, tested implementations. The current V1.0 release should be used for anyone but those needing the absolute latest code.


# Processing data using the GESTALT pipeline

## Before you begin
Things you need to have:
* **If you're using Docker, then a Docker installation**. Follow the documentation here: https://docs.docker.com/engine/installation/. If the pipeline is already installed locally then there's no need. If 

Then download the docker container:
```
docker pull aaronmck/single_cell_gestalt:stable
```

Run and connect to the container, remapping port 80 of the container to port 8080 on your local machine:
```
 docker run -it -p 8080:80 aaronmck/single_cell_gestalt:stable /bin/bash
```

# Setup an individual run

* Reference files. See examples in /app/references/ in the Docker container
  - **reference file (fasta)**: this should be a single entry fasta file containing a standard header line that starts with a ">", followed by the name of the sequence (totally arbritrary). The second line is the reference sequence, which can be split over many lines if you prefer.
  - **A cutsite file**. This should have the same name as the reference file, with the cutSites extension ( <reference.fa>.cutSites, where the <reference.fa> is the name of your reference). This describes where CRISPR/Cas9 binds to the sequence and makes a cut. There are three columns, the first is the sequence of the CRISPR target, the second is the position of the start of that target (5'), and the third is the position of the cutsite (17 basepairs downstream from the start of the target for Cas9). See the example_data directory for an example version. Positions are relative to the start of the reference.
  - **A primers file**, This file is named <reference.fa>.primers. It contains the invariant sequences that are expected to be present at both ends of the amplicon used to PCR-up the amplicon from the background. This file is two lines, a primer on each line **oriented** with the reference. This is used to filter out non-specific PCR products that may be present in your sequencing run. This filtering is optional (you can use both ends, either, or none), and will depend on if this was done with PCR (both ends can be used) or with single-cell approaches where just one end is relevant. 

* **A processing tearsheet**, describing your samples. See the example 'sample_tearsheet.tsv' in the /app/sc_GESTALT/tear_sheet_examples directory. This file can be editing in Excel or a text editor and saved as a tab-delimited file. The columns are as follows:
 - sample: used to create sample-specific output files
 - umi: Do the amplicons have UMIs? These are sequences that uniquely identify  a unique DNA fragment which was amplified by PCR.
   - reference: What reference file (.fa) to use for this sample. This reference should have the cutSites and primers files along-side (in the same directory).
   - output.dir: where to put the output. A directory matching the sample name above will be created within this parent directory
   - fastq1: The fastq.gz file containing the first reads.
   - fastq2: Second read, can be set to NA if there isn't a second read file	
   - barcode.fastq1: barcode (index) file 1
   - barcode.fastq2: barcode (index) file 2
   - barcode1: If we want to split out this sample from the fastq files, this sequence should match what's in the barcode.fastq1 file. Can be set to ALL to include all reads
   - barcode2: If we want to split out this sample from the fastq files, this sequence should match what's in the barcode.fastq2 file. Can be set to ALL to include all reads
  
Again, you can save this to a tab-delimited file on the same filesystem you plan to run the pipeline on.

## Run the example script

Now within the container, run the example script:
```
sh /app/sc_GESTALT/tear_sheet_examples/run_crispr_pipeline.sh
```
## Adjust the existing scripts for your data

You can adjust the input files by changing filenames in the tearsheet: app/sc_GESTALT/tear_sheet_examples/basic_example.txt. To mount your data on a local disk to a location within the docker filesystem, use the ```-v``` option:
```
docker run -m 8g -it -p 8080:80 -v /Users/aaronmck/Desktop/gel_images/:/my_data aaronmck/single_cell_gestalt:SC_GESTALT /bin/bash
```
You can then rerun the example script or create your own:
```
sh /app/sc_GESTALT/tear_sheet_examples/run_crispr_pipeline.sh
```

### Some considerations for your single-cell sequencing run

Often you're using the single-cell sequencing tags (UMIs and cell barcodes) to recover GESTALT tags, which are the result of either whole mRNA pull-down or with a targeted PCR. 


# Output

The example script creates a number of files in the output directory. This location is specified in the input sample spreadsheet, in the *output.dir* column. See our example spreadsheet used in the script here: 

`/app/sc_GESTALT/tear_sheet_examples/basic_example.txt`

In that file the base output location is set to */app/data/*, which should have a sub-directory for the *dome_4_1X*. 

Within the the base output directory there are a couple important output files:

* **dome_4_1X.stats** contains the collapsed reads and the event calls per target site. This is the most important file, as it contains the final collapsed output for individual UMIs.

* **dome_4_1X.umiCounts** information about each UMIs that we saw and if there were enough reads for an individual UMI to call it 'successfully captured'. You should check this file to see if there are any problems with the UMI collapsing step.  

The pipeline also generates visualization output: plots for the editing pattern over the target. The default is to put this into the */var/www/html/* directory on the Docker container, but can be set with the --web parameter to the pipeline. If you've remapped your ports above (using -p 8080:80) you should be able to visualize the output by opening localhost:8080 when run locally. 

Within the the base output directory there will be a directory for each run. In the example case, our run is called *testdata*, and there will be a directory matching this name. In that, our sample is called *dome_4_1X*. This folder contains the html output to visualize editing patterns over your sample. If you've setup your port (the `-p 8080:80` option when running Docker) you can open a browser and view your output here:

http://localhost:8080/testdata/dome_4_1X/read_editing_mutlihistogram.html

Each sample processed should have a `ead_editing_mutlihistogram.html` html file in it's base output directory.
