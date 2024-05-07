/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package gorsat.Utilities

import gorsat.Commands.CommandParseUtilities
import gorsat.Script.ScriptParsers
import gorsat.process
import gorsat.process.{GenericSessionFactory, GorInputSources, GorPipeCommands}
import org.gorpipe.gor.session.GorSession
import org.gorpipe.test.utils.FileTestUtils
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UTestGorPrePipe extends AnyFunSuite with BeforeAndAfterAll {

  protected var ymlPnsTxtPath = ""
  protected var rsID1 = "rs544101329"
  protected var rsID2 = "rs28970552"

  override protected def beforeAll(): Unit = {
    GorPipeCommands.register()
    GorInputSources.register()
    var tempDirectory = FileTestUtils.createTempDirectory(this.getClass.getName)
    var ymlPnsTxt = FileTestUtils.createTempFile(tempDirectory, "rsIDsFile.txt",
      rsID1 + "\n" +
        rsID2)
    ymlPnsTxtPath = ymlPnsTxt.getCanonicalPath
  }

  val session: GorSession = new GenericSessionFactory().create()

  test("Get used file from gor query") {
    val query = "gor ../tests/data/gor/dbsnp_test.gorz | group 100 -fc pos -count | top 10"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "../tests/data/gor/dbsnp_test.gorz")
  }

  test("Get used file from gor nested query") {
    val query = "gor <(../tests/data/gor/dbsnp_test.gorz | group 100 -fc pos -count | top 10)"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "../tests/data/gor/dbsnp_test.gorz")
  }

  test("Get used file from a dictionary with -f option") {
    val query = "gor source/var/wgs_varcalls.gord -s PN -f 'IO_GIAB_MOTHER'|where GL_Call >= 5 and (Depth >= 8 or Depth = -1 or Depth = 9999) and ((CallCopies = 2 and CallRatio >= 0.66) or (CallCopies = 1 and CallRatio >= 0.2 and CallRatio <= 1.0-0.2))|select 1-4,callCopies,Callratio,Depth|calc sumAD = CallRatio*Depth|replace sumAD round(sumAD)|replace Depth round(Depth)|calc ratio_breakdown sumAD+'/'+Depth|calc proband_call Call+' (ref='+Reference+')'|calc hetORhom if(CallCopies=2,'hom',if(CallCopies=1,'het',''))"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "#gordict#source/var/wgs_varcalls.gord#gortags#IO_GIAB_MOTHER")
  }

  test("Get used file from a dictionary with -ff option") {
    val query = "gor source/var/wgs_varcalls.gord -s PN -ff " + ymlPnsTxtPath + "|where GL_Call >= 5 and (Depth >= 8 or Depth = -1 or Depth = 9999) and ((CallCopies = 2 and CallRatio >= 0.66) or (CallCopies = 1 and CallRatio >= 0.2 and CallRatio <= 1.0-0.2))|select 1-4,callCopies,Callratio,Depth|calc sumAD = CallRatio*Depth|replace sumAD round(sumAD)|replace Depth round(Depth)|calc ratio_breakdown sumAD+'/'+Depth|calc proband_call Call+' (ref='+Reference+')'|calc hetORhom if(CallCopies=2,'hom',if(CallCopies=1,'het',''))"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "#gordict#source/var/wgs_varcalls.gord#gortags#" + rsID1 + "," + rsID2)
  }

  test("Get used file from nor query") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | top 10"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "../tests/data/gor/dbsnp_test.gorz")
  }

  test("Get used file from gor query with join") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | join -snpsnp multicolumns.gor"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 2)
    assert(result(1) == "../tests/data/gor/dbsnp_test.gorz")
    assert(result.head == "multicolumns.gor")
  }

  test("Get used file from gor query with join which has a nested source") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | join -snpsnp <(gor multicolumns.gor | top 10)"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 2)
    assert(result(1) == "../tests/data/gor/dbsnp_test.gorz")
    assert(result.head == "multicolumns.gor")
  }

  test("Get used file from gor query with map") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz |map -c foo multicolumns.gor"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 2)
    assert(result(1) == "../tests/data/gor/dbsnp_test.gorz")
    assert(result.head == "multicolumns.gor")
  }

  test("Get used file from gor query with map which has a nested source") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | map -c foo  <(nor multicolumns.gor | top 10)"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 2)
    assert(result(1) == "../tests/data/gor/dbsnp_test.gorz")
    assert(result.head == "multicolumns.gor")
  }

  test("Get used file from gor query with multimap") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | multimap -c foo multicolumns.gor"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 2)
    assert(result(1) == "../tests/data/gor/dbsnp_test.gorz")
    assert(result.head == "multicolumns.gor")
  }

  test("Get used file from gor query with multimap which has a nested source") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | multimap -c foo  <(nor multicolumns.gor | top 10)"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 2)
    assert(result(1) == "../tests/data/gor/dbsnp_test.gorz")
    assert(result.head == "multicolumns.gor")
  }

  test("Get used files from gor query with multiple source based steps") {
    val query = "nor ../tests/data/gor/dbsnp_test.gorz | multimap -c foo 1.gor | map -c foo 2.gor | inset -c foo 3.gor | merge 4.gor | varjoin 5.gor | join 6.gor"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 7)
    assert(result.head == "6.gor")
    assert(result(1) == "5.gor")
    assert(result(2) == "4.gor")
    assert(result(3) == "3.gor")
    assert(result(4) == "2.gor")
    assert(result(5) == "1.gor")
    assert(result(6) == "../tests/data/gor/dbsnp_test.gorz")
  }

  test("Get used files from gor script") {
    val query = "create ##dummy## = gor ##genes## | top 1; \n \n def ##ref## = ref; \n def ##genes## = ##ref##/ensgenes/genes.gorz; \n def ##freqMax## = ##ref##/variants/freq_max.gorz; \n def ##dbnsfpmax## = ##ref##/variants/dbnsfp_max.gorz; \n def ##dbsnp## = ##ref##/variants/dbsnp.gorz; \n def ##gmap## = ##ref##/ensgenes/ensgenes.map; \n \n create ##gene_cov_and_cand_info## = gorrow chr1,10039,10039 \n | calc Reference 'A' \n | calc Call 'C' \n | calc gene_symbol 'APOE' \n | hide #3 \n | rename #2 Pos \n | join -varseg -l -f 10 -r -rprefix GENE -xl gene_symbol -xr gene_symbol <(gor source/cov/gene_cov_coding_seg.gord -s PN -f 'IO_GIAB_MOTHER' | map -c gene_symbol ##gmap## -n gene_aliases -m 'missing' | split gene_aliases | replace gene_symbol if(gene_aliases != 'missing',gene_aliases,gene_symbol) | hide gene_aliases) \n | calc gene_cov if(isfloat(gene_lt5),'L:'+form(gene_lt5,4,2)+'M:'+form((gene_lt10-gene_lt5),4,2)+'H:'+form((1-gene_lt10),4,2),'NaN') | select 1-4,gene_cov; \n \n gorrow chr1,10039,10039 \n | calc Reference 'A' \n | calc Call 'C' \n | calc gene_symbol 'APOE' \n | hide #3 \n | rename #2 Pos \n | varjoin -l -r -e '0.0' <(gor ##freqMax## | select 1-4,max_af | distinct | replace max_af form(max_af,5,5)) \n | varjoin -l -r -e 'NaN' <(gor ##dbnsfpmax## | calc Max_Score max(max(if(isfloat(Polyphen2_HDIV_score),Polyphen2_HDIV_score,0),if(isfloat(Polyphen2_HVAR_score),Polyphen2_HVAR_score,0)),if(isfloat(Sift_score),Sift_score,0)) | group 1 -gc #3,#4 -max -fc max_score | rename max_max_score Max_Score) \n | varjoin -l -r -e 'NaN' <(gor ##dbsnp##) \n \n | varjoin -l -r -e 'NaN' <(gor #wgsVars# -f 'IO_GIAB_MOTHER' \n | where GL_Call >= 5 and (Depth >= 8 or Depth = -1 or Depth = 9999) and ((CallCopies = 2 and CallRatio >= 0.66) or (CallCopies = 1 and CallRatio >= 0.2 and CallRatio <= 1.0-0.2))\n | select 1-4,callCopies,Callratio,Depth \n | calc sumAD = CallRatio*Depth \n | replace sumAD round(sumAD) \n | replace Depth round(Depth) \n | calc ratio_breakdown sumAD+'/'+Depth \n | calc proband_call Call+' (ref='+Reference+')' \n | calc hetORhom if(CallCopies=2,'hom',if(CallCopies=1,'het',''))\n \n | calc varType if(len(reference)=len(call),'sub',if(len(call)<len(reference) and substr(reference,0,len(call)) = call,'del',if(len(call)>len(reference) and substr(call,0,len(reference)) = reference,'ins','indel') )) \n | calc hetORhom_type if(hetORhom != 'NaN',hetORhom+'_','')+varType | hide sumAD,Depth,Callcopies,hetOrhom,varType) \n | varjoin -l -r -xr gene_symbol -xl gene_symbol -e 'NaN' <(gor source/anno/vep_v85/vep_single_wgs.gord | select 1-4,gene_symbol,max_impact,max_consequence) \n | hide gene_symbolx | varjoin -l -r -e 'NaN' <(gor [##gene_cov_and_cand_info##])\n"
    var commands = CommandParseUtilities.quoteSafeSplit(query.replace('\n', ' '), ';')
    val aliases = MacroUtilities.extractAliases(commands)
    commands = MacroUtilities.applyAliases(commands, aliases)
    val session = this.session
    var files = List.empty[String]

    commands.foreach { x =>
      val (a, b) = ScriptParsers.createParser(x)
      if (a.nonEmpty)
        files :::= process.GorPrePipe.getUsedFiles(b, session)
      else
        files :::= process.GorPrePipe.getUsedFiles(x, session)
    }

    assert(files.length == 9)
    assert(files.head == "[##gene_cov_and_cand_info##]")
    assert(files(7) == "#gordict#source/cov/gene_cov_coding_seg.gord#gortags#IO_GIAB_MOTHER")
    assert(files(8) == "ref/ensgenes/genes.gorz")
  }

  test("Get used file from gor dictionary query") {
    val query = "gor ../tests/data/tmp.gord -f 'foo','bar'"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "#gordict#../tests/data/tmp.gord#gortags#bar,foo")
  }

  test("Get used file from nor dictionary query") {
    val query = "nor ../tests/data/tmp.nord -f 'foo','bar'"
    val result = process.GorPrePipe.getUsedFiles(query, session)

    assert(result.length == 1)
    assert(result.head == "#gordict#../tests/data/tmp.nord#gortags#bar,foo")
  }

//  test("Huge filter of gor dictionary") {
//    val query = "gor UKBB/genotype_array/array.gord -nf -f 1006663,1013456,1014112,1017332,1034886,1035605,1036322,1043322,1049526,1050361,1054429,1057237,1063094,1063990,1069018,1071115,1075344,1083076,1094282,1096615,1122058,1125316,1127334,1129282,1130100,1139135,1144230,1147118,1148853,1150752,1150950,1151920,1153347,1156694,1163748,1170208,1178807,1182299,1186306,1191134,1192462,1195287,1197389,1204322,1208833,1211415,1215241,1221204,1225676,1226539,1231279,1247245,1250341,1255095,1264425,1272780,1272951,1276982,1279771,1285151,1289841,1297563,1301052,1303611,1315322,1329247,1331715,1332331,1348110,1356047,1368322,1369449,1373169,1375860,1377050,1381298,1384953,1385867,1405676,1406272,1407264,1411555,1412515,1421437,1422435,1424152,1428928,1436812,1440012,1441397,1446203,1469491,1475873,1476823,1481776,1481985,1486668,1499844,1512653,1515004,1515292,1517170,1521612,1531249,1533643,1540099,1547443,1548837,1553656,1556103,1567520,1573214,1580476,1586518,1592447,1598655,1616129,1617233,1619363,1622886,1625963,1626221,1634393,1640850,1646414,1647765,1647896,1664905,1676785,1681290,1684413,1688030,1689333,1692356,1709053,1711273,1717510,1718957,1721931,1727559,1728850,1729940,1737890,1738490,1747478,1757960,1768477,1768600,1771466,1787848,1788328,1792241,1794190,1795257,1805574,1807600,1810045,1816197,1829345,1830868,1831497,1841479,1842125,1849120,1849655,1851333,1852903,1857473,1859384,1861462,1865336,1872104,1887381,1899279,1901135,1905565,1910325,1910436,1913990,1914988,1918157,1918261,1925435,1927724,1930906,1932885,1933774,1943073,1945603,1948308,1952000,1952263,1975987,1981495,1986006,1995517,1997382,2003161,2009334,2014709,2016078,2032975,2053029,2061702,2064306,2069639,2071462,2074507,2077745,2084898,2088812,2102604,2103232,2104830,2104952,2110844,2113669,2122133,2126676,2133768,2138140,2141557,2146794,2150582,2151636,2154616,2170720,2174024,2179053,2179428,2182550,2184470,2196238,2202956,2232724,2239002,2245686,2246325,2249774,2251799,2269022,2269404,2273990,2289361,2289593,2292920,2293489,2299993,2304963,2308814,2311838,2324845,2328912,2330946,2334061,2343171,2346558,2347170,2348345,2355917,2369439,2377012,2378966,2379474,2381552,2383768,2386582,2387536,2390847,2392423,2397662,2397670,2410684,2418314,2425720,2432829,2433217,2445153,2450098,2462415,2462439,2474041,2474380,2483811,2502243,2503912,2510010,2512481,2512794,2519184,2522028,2531785,2535097,2537792,2539552,2545221,2550965,2557327,2558383,2558442,2583348,2588001,2595255,2596571,2598582,2606382,2622782,2626227,2632444,2634361,2635784,2636910,2638788,2643936,2645449,2646192,2649575,2650591,2657574,2665778,2671423,2689994,2702157,2702541,2703378,2704505,2704607,2706676,2709218,2709407,2710454,2715245,2716645,2719535,2728389,2730291,2737201,2737256,2743271,2744595,2745112,2747934,2749262,2751409,2751705,2760546,2768024,2768052,2774296,2778753,2781099,2781430,2783801,2790661,2800488,2806401,2808623,2809042,2811224,2815627,2817646,2819168,2819564,2821773,2826988,2843931,2846249,2857573,2860350,2863709,2867966,2868448,2869017,2887372,2900963,2901718,2902642,2908761,2917154,2917934,2919319,2920453,2922990,2926282,2929667,2934390,2936750,2936823,2938765,2943214,2944988,2948011,2950051,2955906,2956914,2969461,2971340,2978303,2984113,2987527,2987596,2995784,2995967,3000050,3007969,3008699,3014267,3026425,3026784,3036503,3039488,3041476,3054350,3057698,3057737,3069948,3086005,3087318,3090846,3093949,3112251,3115117,3116831,3133795,3134918,3137082,3137250,3140803,3145023,3148152,3150153,3160984,3177328,3178789,3186195,3186834,3187352,3194274,3197268,3199444,3199460,3200347,3200506,3202411,3204956,3209304,3218997,3221798,3223282,3226970,3236890,3239606,3243648,3249571,3263775,3271155,3272254,3274998,3275321,3278063,3279830,3284162,3290229,3294457,3305720,3323677,3329454,3332416,3343719,3350964,3362786,3366871,3369370,3369697,3370445,3389805,3395318,3411324,3411978,3417399,3419695,3419936,3423748,3429420,3430186,3431304,3434103,3434818,3439010,3444076,3449567,3453307,3454312,3458584,3461581,3477559,3484125,3501058,3504509,3507577,3507656,3514535,3530546,3534787,3541165,3543571,3554328,3556197,3557289,3560318,3562670,3576781,3584860,3589155,3590403,3593008,3599262,3607342,3614654,3623140,3627195,3633515,3636491,3640849,3643841,3644384,3651446,3658603,3662431,3664742,3664872,3673874,3679200,3682355,3682467,3687272,3690834,3696670,3697116,3698694,3703640,3703865,3705035,3710572,3710948,3714149,3715153,3720582,3726475,3739182,3760206,3772972,3774163,3777368,3785907,3789351,3790042,3791440,3792066,3795154,3800061,3804830,3807412,3815167,3823608,3832276,3835848,3840735,3845259,3848476,3849252,3851102,3866599,3872613,3880445,3881346,3882153,3883122,3887544,3888149,3889958,3891050,3892440,3903907,3905123,3907344,3910578,3914395,3923646,3932206,3933299,3942291,3949372,3953944,3954813,3964579,3967345,3967818,3969342,3975578,3975671,3987791,3996199,3998220,3998840,4010458,4024972,4025618,4027527,4029115,4031076,4032786,4037883,4038410,4042951,4043144,4045603,4047110,4055045,4064315,4071168,4073683,4091011,4092587,4100995,4122624,4127346,4128032,4135091,4135140,4136141,4149710,4150646,4152031,4160133,4177426,4181251,4187023,4193580,4203267,4208519,4210878,4214665,4216424,4217366,4223590,4224687,4226822,4228087,4235281,4241543,4244692,4244770,4272281,4273338,4274832,4279785,4291862,4300836,4301799,4304935,4306296,4308671,4310185,4318885,4320898,4325186,4331884,4332672,4336930,4339753,4348126,4357429,4360713,4362458,4363362,4367803,4369709,4370313,4372165,4372787,4374131,4374496,4379729,4386793,4391039,4396711,4397296,4401319,4416089,4416261,4427274,4428358,4429899,4434692,4436387,4439236,4440231,4444457,4453837,4458081,4462985,4463403,4469461,4469764,4470564,4471968,4487498,4492487,4499370,4505907,4515948,4517982,4518403,4518853,4519541,4520117,4520163,4522598,4529474,4530517,4531155,4532735,4536906,4541959,4553150,4564910,4576130,4578761,4597753,4601310,4605701,4605988,4605996,4610454,4614120,4637588,4652947,4655531,4662377,4670748,4681571,4685552,4696640,4696946,4699052,4703167,4712776,4714921,4728716,4729236,4732544,4733883,4745887,4747187,4755811,4758364,4758725,4760750,4764022,4811930,4816698,4818161,4825297,4826061,4828656,4840998,4843451,4853596,4853613,4868574,4871604,4872157,4879612,4887605,4891823,4899298,4904470,4908869,4909206,4916384,4924544,4925597,4931588,4949086,4957110,4961643,4964213,4965143,4965681,4970323,4977496,4979598,4984257,4988879,4996124,4997244,5014983,5021218,5025058,5040482,5049105,5055942,5060450,5060748,5061828,5063867,5066961,5082609,5087511,5087960,5090786,5093983,5101633,5102941,5103487,5113257,5129547,5131769,5148626,5160167,5179760,5181793,5184762,5187803,5193594,5200886,5204280,5216646,5218147,5219692,5224240,5225602,5225823,5228612,5236605,5238190,5239603,5239964,5250298,5260268,5261410,5271254,5272727,5276158,5276705,5284319,5284602,5286643,5306195,5307934,5313446,5322503,5322993,5323634,5332167,5344559,5356950,5360079,5372730,5375241,5375955,5380353,5382298,5382605,5388004,5389876,5391151,5392409,5393139,5406050,5412313,5414077,5416925,5417984,5427829,5435791,5438449,5449638,5450781,5455794,5457736,5462253,5464161,5467636,5472897,5474713,5478789,5481668,5488494,5490049,5494656,5494937,5503156,5511220,5515283,5518728,5521089,5523822,5535472,5538068,5542449,5547125,5551122,5559969,5568186,5574253,5582615,5584704,5590127,5594786,5598128,5602665,5610734,5611915,5615976,5627491,5630160,5632116,5632366,5632373,5643024,5646866,5647244,5657689,5662214,5666545,5667826,5668720,5684495,5697317,5701131,5709699,5717921,5721525,5724168,5730561,5740826,5745079,5746475,5749703,5751235,5753479,5754092,5755620,5782746,5801136,5816791,5827776,5829026,5838563,5841109,5843344,5847963,5848541,5849031,5857354,5857771,5862315,5863237,5872478,5873373,5879453,5883170,5888432,5891661,5909384,5909899,5910315,5912583,5912757,5915094,5915664,5916300,5919260,5921006,5921911,5936370,5936854,5940437,5951937,5954012,5957080,5957246,5961570,5973472,5974626,5982732,5986001,5986379,5993239,5999079,6000543,6002704,6003207,6003364,6005338,6013034,6015144,6021782 | top 10 | calc x listsize('1006663,1013456,1014112,1017332,1034886,1035605,1036322,1043322,1049526,1050361,1054429,1057237,1063094,1063990,1069018,1071115,1075344,1083076,1094282,1096615,1122058,1125316,1127334,1129282,1130100,1139135,1144230,1147118,1148853,1150752,1150950,1151920,1153347,1156694,1163748,1170208,1178807,1182299,1186306,1191134,1192462,1195287,1197389,1204322,1208833,1211415,1215241,1221204,1225676,1226539,1231279,1247245,1250341,1255095,1264425,1272780,1272951,1276982,1279771,1285151,1289841,1297563,1301052,1303611,1315322,1329247,1331715,1332331,1348110,1356047,1368322,1369449,1373169,1375860,1377050,1381298,1384953,1385867,1405676,1406272,1407264,1411555,1412515,1421437,1422435,1424152,1428928,1436812,1440012,1441397,1446203,1469491,1475873,1476823,1481776,1481985,1486668,1499844,1512653,1515004,1515292,1517170,1521612,1531249,1533643,1540099,1547443,1548837,1553656,1556103,1567520,1573214,1580476,1586518,1592447,1598655,1616129,1617233,1619363,1622886,1625963,1626221,1634393,1640850,1646414,1647765,1647896,1664905,1676785,1681290,1684413,1688030,1689333,1692356,1709053,1711273,1717510,1718957,1721931,1727559,1728850,1729940,1737890,1738490,1747478,1757960,1768477,1768600,1771466,1787848,1788328,1792241,1794190,1795257,1805574,1807600,1810045,1816197,1829345,1830868,1831497,1841479,1842125,1849120,1849655,1851333,1852903,1857473,1859384,1861462,1865336,1872104,1887381,1899279,1901135,1905565,1910325,1910436,1913990,1914988,1918157,1918261,1925435,1927724,1930906,1932885,1933774,1943073,1945603,1948308,1952000,1952263,1975987,1981495,1986006,1995517,1997382,2003161,2009334,2014709,2016078,2032975,2053029,2061702,2064306,2069639,2071462,2074507,2077745,2084898,2088812,2102604,2103232,2104830,2104952,2110844,2113669,2122133,2126676,2133768,2138140,2141557,2146794,2150582,2151636,2154616,2170720,2174024,2179053,2179428,2182550,2184470,2196238,2202956,2232724,2239002,2245686,2246325,2249774,2251799,2269022,2269404,2273990,2289361,2289593,2292920,2293489,2299993,2304963,2308814,2311838,2324845,2328912,2330946,2334061,2343171,2346558,2347170,2348345,2355917,2369439,2377012,2378966,2379474,2381552,2383768,2386582,2387536,2390847,2392423,2397662,2397670,2410684,2418314,2425720,2432829,2433217,2445153,2450098,2462415,2462439,2474041,2474380,2483811,2502243,2503912,2510010,2512481,2512794,2519184,2522028,2531785,2535097,2537792,2539552,2545221,2550965,2557327,2558383,2558442,2583348,2588001,2595255,2596571,2598582,2606382,2622782,2626227,2632444,2634361,2635784,2636910,2638788,2643936,2645449,2646192,2649575,2650591,2657574,2665778,2671423,2689994,2702157,2702541,2703378,2704505,2704607,2706676,2709218,2709407,2710454,2715245,2716645,2719535,2728389,2730291,2737201,2737256,2743271,2744595,2745112,2747934,2749262,2751409,2751705,2760546,2768024,2768052,2774296,2778753,2781099,2781430,2783801,2790661,2800488,2806401,2808623,2809042,2811224,2815627,2817646,2819168,2819564,2821773,2826988,2843931,2846249,2857573,2860350,2863709,2867966,2868448,2869017,2887372,2900963,2901718,2902642,2908761,2917154,2917934,2919319,2920453,2922990,2926282,2929667,2934390,2936750,2936823,2938765,2943214,2944988,2948011,2950051,2955906,2956914,2969461,2971340,2978303,2984113,2987527,2987596,2995784,2995967,3000050,3007969,3008699,3014267,3026425,3026784,3036503,3039488,3041476,3054350,3057698,3057737,3069948,3086005,3087318,3090846,3093949,3112251,3115117,3116831,3133795,3134918,3137082,3137250,3140803,3145023,3148152,3150153,3160984,3177328,3178789,3186195,3186834,3187352,3194274,3197268,3199444,3199460,3200347,3200506,3202411,3204956,3209304,3218997,3221798,3223282,3226970,3236890,3239606,3243648,3249571,3263775,3271155,3272254,3274998,3275321,3278063,3279830,3284162,3290229,3294457,3305720,3323677,3329454,3332416,3343719,3350964,3362786,3366871,3369370,3369697,3370445,3389805,3395318,3411324,3411978,3417399,3419695,3419936,3423748,3429420,3430186,3431304,3434103,3434818,3439010,3444076,3449567,3453307,3454312,3458584,3461581,3477559,3484125,3501058,3504509,3507577,3507656,3514535,3530546,3534787,3541165,3543571,3554328,3556197,3557289,3560318,3562670,3576781,3584860,3589155,3590403,3593008,3599262,3607342,3614654,3623140,3627195,3633515,3636491,3640849,3643841,3644384,3651446,3658603,3662431,3664742,3664872,3673874,3679200,3682355,3682467,3687272,3690834,3696670,3697116,3698694,3703640,3703865,3705035,3710572,3710948,3714149,3715153,3720582,3726475,3739182,3760206,3772972,3774163,3777368,3785907,3789351,3790042,3791440,3792066,3795154,3800061,3804830,3807412,3815167,3823608,3832276,3835848,3840735,3845259,3848476,3849252,3851102,3866599,3872613,3880445,3881346,3882153,3883122,3887544,3888149,3889958,3891050,3892440,3903907,3905123,3907344,3910578,3914395,3923646,3932206,3933299,3942291,3949372,3953944,3954813,3964579,3967345,3967818,3969342,3975578,3975671,3987791,3996199,3998220,3998840,4010458,4024972,4025618,4027527,4029115,4031076,4032786,4037883,4038410,4042951,4043144,4045603,4047110,4055045,4064315,4071168,4073683,4091011,4092587,4100995,4122624,4127346,4128032,4135091,4135140,4136141,4149710,4150646,4152031,4160133,4177426,4181251,4187023,4193580,4203267,4208519,4210878,4214665,4216424,4217366,4223590,4224687,4226822,4228087,4235281,4241543,4244692,4244770,4272281,4273338,4274832,4279785,4291862,4300836,4301799,4304935,4306296,4308671,4310185,4318885,4320898,4325186,4331884,4332672,4336930,4339753,4348126,4357429,4360713,4362458,4363362,4367803,4369709,4370313,4372165,4372787,4374131,4374496,4379729,4386793,4391039,4396711,4397296,4401319,4416089,4416261,4427274,4428358,4429899,4434692,4436387,4439236,4440231,4444457,4453837,4458081,4462985,4463403,4469461,4469764,4470564,4471968,4487498,4492487,4499370,4505907,4515948,4517982,4518403,4518853,4519541,4520117,4520163,4522598,4529474,4530517,4531155,4532735,4536906,4541959,4553150,4564910,4576130,4578761,4597753,4601310,4605701,4605988,4605996,4610454,4614120,4637588,4652947,4655531,4662377,4670748,4681571,4685552,4696640,4696946,4699052,4703167,4712776,4714921,4728716,4729236,4732544,4733883,4745887,4747187,4755811,4758364,4758725,4760750,4764022,4811930,4816698,4818161,4825297,4826061,4828656,4840998,4843451,4853596,4853613,4868574,4871604,4872157,4879612,4887605,4891823,4899298,4904470,4908869,4909206,4916384,4924544,4925597,4931588,4949086,4957110,4961643,4964213,4965143,4965681,4970323,4977496,4979598,4984257,4988879,4996124,4997244,5014983,5021218,5025058,5040482,5049105,5055942,5060450,5060748,5061828,5063867,5066961,5082609,5087511,5087960,5090786,5093983,5101633,5102941,5103487,5113257,5129547,5131769,5148626,5160167,5179760,5181793,5184762,5187803,5193594,5200886,5204280,5216646,5218147,5219692,5224240,5225602,5225823,5228612,5236605,5238190,5239603,5239964,5250298,5260268,5261410,5271254,5272727,5276158,5276705,5284319,5284602,5286643,5306195,5307934,5313446,5322503,5322993,5323634,5332167,5344559,5356950,5360079,5372730,5375241,5375955,5380353,5382298,5382605,5388004,5389876,5391151,5392409,5393139,5406050,5412313,5414077,5416925,5417984,5427829,5435791,5438449,5449638,5450781,5455794,5457736,5462253,5464161,5467636,5472897,5474713,5478789,5481668,5488494,5490049,5494656,5494937,5503156,5511220,5515283,5518728,5521089,5523822,5535472,5538068,5542449,5547125,5551122,5559969,5568186,5574253,5582615,5584704,5590127,5594786,5598128,5602665,5610734,5611915,5615976,5627491,5630160,5632116,5632366,5632373,5643024,5646866,5647244,5657689,5662214,5666545,5667826,5668720,5684495,5697317,5701131,5709699,5717921,5721525,5724168,5730561,5740826,5745079,5746475,5749703,5751235,5753479,5754092,5755620,5782746,5801136,5816791,5827776,5829026,5838563,5841109,5843344,5847963,5848541,5849031,5857354,5857771,5862315,5863237,5872478,5873373,5879453,5883170,5888432,5891661,5909384,5909899,5910315,5912583,5912757,5915094,5915664,5916300,5919260,5921006,5921911,5936370,5936854,5940437,5951937,5954012,5957080,5957246,5961570,5973472,5974626,5982732,5986001,5986379,5993239,5999079,6000543,6002704,6003207,6003364,6005338,6013034,6015144,6021782')"
//    val result = process.GorPrePipe.getUsedFiles(query, session)
//    assert(result.length == 1)
//    assert(result.head == "#gordict#UKBB/genotype_array/array.gord#gortags#1006663,1013456,1014112,1017332,1034886,1035605,1036322,1043322,1049526,1050361,1054429,1057237,1063094,1063990,1069018,1071115,1075344,1083076,1094282,1096615,1122058,1125316,1127334,1129282,1130100,1139135,1144230,1147118,1148853,1150752,1150950,1151920,1153347,1156694,1163748,1170208,1178807,1182299,1186306,1191134,1192462,1195287,1197389,1204322,1208833,1211415,1215241,1221204,1225676,1226539,1231279,1247245,1250341,1255095,1264425,1272780,1272951,1276982,1279771,1285151,1289841,1297563,1301052,1303611,1315322,1329247,1331715,1332331,1348110,1356047,1368322,1369449,1373169,1375860,1377050,1381298,1384953,1385867,1405676,1406272,1407264,1411555,1412515,1421437,1422435,1424152,1428928,1436812,1440012,1441397,1446203,1469491,1475873,1476823,1481776,1481985,1486668,1499844,1512653,1515004,1515292,1517170,1521612,1531249,1533643,1540099,1547443,1548837,1553656,1556103,1567520,1573214,1580476,1586518,1592447,1598655,1616129,1617233,1619363,1622886,1625963,1626221,1634393,1640850,1646414,1647765,1647896,1664905,1676785,1681290,1684413,1688030,1689333,1692356,1709053,1711273,1717510,1718957,1721931,1727559,1728850,1729940,1737890,1738490,1747478,1757960,1768477,1768600,1771466,1787848,1788328,1792241,1794190,1795257,1805574,1807600,1810045,1816197,1829345,1830868,1831497,1841479,1842125,1849120,1849655,1851333,1852903,1857473,1859384,1861462,1865336,1872104,1887381,1899279,1901135,1905565,1910325,1910436,1913990,1914988,1918157,1918261,1925435,1927724,1930906,1932885,1933774,1943073,1945603,1948308,1952000,1952263,1975987,1981495,1986006,1995517,1997382,2003161,2009334,2014709,2016078,2032975,2053029,2061702,2064306,2069639,2071462,2074507,2077745,2084898,2088812,2102604,2103232,2104830,2104952,2110844,2113669,2122133,2126676,2133768,2138140,2141557,2146794,2150582,2151636,2154616,2170720,2174024,2179053,2179428,2182550,2184470,2196238,2202956,2232724,2239002,2245686,2246325,2249774,2251799,2269022,2269404,2273990,2289361,2289593,2292920,2293489,2299993,2304963,2308814,2311838,2324845,2328912,2330946,2334061,2343171,2346558,2347170,2348345,2355917,2369439,2377012,2378966,2379474,2381552,2383768,2386582,2387536,2390847,2392423,2397662,2397670,2410684,2418314,2425720,2432829,2433217,2445153,2450098,2462415,2462439,2474041,2474380,2483811,2502243,2503912,2510010,2512481,2512794,2519184,2522028,2531785,2535097,2537792,2539552,2545221,2550965,2557327,2558383,2558442,2583348,2588001,2595255,2596571,2598582,2606382,2622782,2626227,2632444,2634361,2635784,2636910,2638788,2643936,2645449,2646192,2649575,2650591,2657574,2665778,2671423,2689994,2702157,2702541,2703378,2704505,2704607,2706676,2709218,2709407,2710454,2715245,2716645,2719535,2728389,2730291,2737201,2737256,2743271,2744595,2745112,2747934,2749262,2751409,2751705,2760546,2768024,2768052,2774296,2778753,2781099,2781430,2783801,2790661,2800488,2806401,2808623,2809042,2811224,2815627,2817646,2819168,2819564,2821773,2826988,2843931,2846249,2857573,2860350,2863709,2867966,2868448,2869017,2887372,2900963,2901718,2902642,2908761,2917154,2917934,2919319,2920453,2922990,2926282,2929667,2934390,2936750,2936823,2938765,2943214,2944988,2948011,2950051,2955906,2956914,2969461,2971340,2978303,2984113,2987527,2987596,2995784,2995967,3000050,3007969,3008699,3014267,3026425,3026784,3036503,3039488,3041476,3054350,3057698,3057737,3069948,3086005,3087318,3090846,3093949,3112251,3115117,3116831,3133795,3134918,3137082,3137250,3140803,3145023,3148152,3150153,3160984,3177328,3178789,3186195,3186834,3187352,3194274,3197268,3199444,3199460,3200347,3200506,3202411,3204956,3209304,3218997,3221798,3223282,3226970,3236890,3239606,3243648,3249571,3263775,3271155,3272254,3274998,3275321,3278063,3279830,3284162,3290229,3294457,3305720,3323677,3329454,3332416,3343719,3350964,3362786,3366871,3369370,3369697,3370445,3389805,3395318,3411324,3411978,3417399,3419695,3419936,3423748,3429420,3430186,3431304,3434103,3434818,3439010,3444076,3449567,3453307,3454312,3458584,3461581,3477559,3484125,3501058,3504509,3507577,3507656,3514535,3530546,3534787,3541165,3543571,3554328,3556197,3557289,3560318,3562670,3576781,3584860,3589155,3590403,3593008,3599262,3607342,3614654,3623140,3627195,3633515,3636491,3640849,3643841,3644384,3651446,3658603,3662431,3664742,3664872,3673874,3679200,3682355,3682467,3687272,3690834,3696670,3697116,3698694,3703640,3703865,3705035,3710572,3710948,3714149,3715153,3720582,3726475,3739182,3760206,3772972,3774163,3777368,3785907,3789351,3790042,3791440,3792066,3795154,3800061,3804830,3807412,3815167,3823608,3832276,3835848,3840735,3845259,3848476,3849252,3851102,3866599,3872613,3880445,3881346,3882153,3883122,3887544,3888149,3889958,3891050,3892440,3903907,3905123,3907344,3910578,3914395,3923646,3932206,3933299,3942291,3949372,3953944,3954813,3964579,3967345,3967818,3969342,3975578,3975671,3987791,3996199,3998220,3998840,4010458,4024972,4025618,4027527,4029115,4031076,4032786,4037883,4038410,4042951,4043144,4045603,4047110,4055045,4064315,4071168,4073683,4091011,4092587,4100995,4122624,4127346,4128032,4135091,4135140,4136141,4149710,4150646,4152031,4160133,4177426,4181251,4187023,4193580,4203267,4208519,4210878,4214665,4216424,4217366,4223590,4224687,4226822,4228087,4235281,4241543,4244692,4244770,4272281,4273338,4274832,4279785,4291862,4300836,4301799,4304935,4306296,4308671,4310185,4318885,4320898,4325186,4331884,4332672,4336930,4339753,4348126,4357429,4360713,4362458,4363362,4367803,4369709,4370313,4372165,4372787,4374131,4374496,4379729,4386793,4391039,4396711,4397296,4401319,4416089,4416261,4427274,4428358,4429899,4434692,4436387,4439236,4440231,4444457,4453837,4458081,4462985,4463403,4469461,4469764,4470564,4471968,4487498,4492487,4499370,4505907,4515948,4517982,4518403,4518853,4519541,4520117,4520163,4522598,4529474,4530517,4531155,4532735,4536906,4541959,4553150,4564910,4576130,4578761,4597753,4601310,4605701,4605988,4605996,4610454,4614120,4637588,4652947,4655531,4662377,4670748,4681571,4685552,4696640,4696946,4699052,4703167,4712776,4714921,4728716,4729236,4732544,4733883,4745887,4747187,4755811,4758364,4758725,4760750,4764022,4811930,4816698,4818161,4825297,4826061,4828656,4840998,4843451,4853596,4853613,4868574,4871604,4872157,4879612,4887605,4891823,4899298,4904470,4908869,4909206,4916384,4924544,4925597,4931588,4949086,4957110,4961643,4964213,4965143,4965681,4970323,4977496,4979598,4984257,4988879,4996124,4997244,5014983,5021218,5025058,5040482,5049105,5055942,5060450,5060748,5061828,5063867,5066961,5082609,5087511,5087960,5090786,5093983,5101633,5102941,5103487,5113257,5129547,5131769,5148626,5160167,5179760,5181793,5184762,5187803,5193594,5200886,5204280,5216646,5218147,5219692,5224240,5225602,5225823,5228612,5236605,5238190,5239603,5239964,5250298,5260268,5261410,5271254,5272727,5276158,5276705,5284319,5284602,5286643,5306195,5307934,5313446,5322503,5322993,5323634,5332167,5344559,5356950,5360079,5372730,5375241,5375955,5380353,5382298,5382605,5388004,5389876,5391151,5392409,5393139,5406050,5412313,5414077,5416925,5417984,5427829,5435791,5438449,5449638,5450781,5455794,5457736,5462253,5464161,5467636,5472897,5474713,5478789,5481668,5488494,5490049,5494656,5494937,5503156,5511220,5515283,5518728,5521089,5523822,5535472,5538068,5542449,5547125,5551122,5559969,5568186,5574253,5582615,5584704,5590127,5594786,5598128,5602665,5610734,5611915,5615976,5627491,5630160,5632116,5632366,5632373,5643024,5646866,5647244,5657689,5662214,5666545,5667826,5668720,5684495,5697317,5701131,5709699,5717921,5721525,5724168,5730561,5740826,5745079,5746475,5749703,5751235,5753479,5754092,5755620,5782746,5801136,5816791,5827776,5829026,5838563,5841109,5843344,5847963,5848541,5849031,5857354,5857771,5862315,5863237,5872478,5873373,5879453,5883170,5888432,5891661,5909384,5909899,5910315,5912583,5912757,5915094,5915664,5916300,5919260,5921006,5921911,5936370,5936854,5940437,5951937,5954012,5957080,5957246,5961570,5973472,5974626,5982732,5986001,5986379,5993239,5999079,6000543,6002704,6003207,6003364,6005338,6013034,6015144,6021782")
//  }
}