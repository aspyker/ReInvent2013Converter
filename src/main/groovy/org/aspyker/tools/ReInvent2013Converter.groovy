package org.aspyker.tools

import org.apache.log4j.Logger
import org.ccil.cowan.tagsoup.Parser
import org.joda.time.DateTime
import com.benfante.jslideshare.SlideShareAPI
import com.benfante.jslideshare.SlideShareAPIFactory
import com.benfante.jslideshare.messages.Slideshow
import com.benfante.jslideshare.messages.User
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import com.google.gdata.client.youtube.YouTubeQuery
import com.google.gdata.client.youtube.YouTubeService
import com.google.gdata.data.youtube.VideoEntry
import com.google.gdata.data.youtube.VideoFeed
import groovy.util.slurpersupport.GPathResult


Logger log = Logger.getLogger(this.class)

def config = new ConfigSlurper().parse(new File('src/main/groovy/config.groovy').toURI().toURL())

def String convertndashes(String orig) {
	// speaker and abstract has &ndash; which comes in as 0x2013 (8211), need to convert back to '-'
	char ndash = '\u2013'
	orig.replace(ndash, (char)'-')
}

def Slideshow[] getAmazonSlides(Logger log, ConfigObject config) {
	log.debug 'config = ' + config
	SlideShareAPI ssapi = SlideShareAPIFactory.getSlideShareAPI(
		config.slideshare.apikey,
		config.slideshare.sharedsecret
	)
	
	def shows = []
	page = 0
	while (true) {
		start = page * 100
		User awsUser = ssapi.getSlideshowByUser('AmazonWebServices', start, 100)
		returned = awsUser.slideshows.size()
		shows.addAll(awsUser.slideshows)
		if (returned < 100) {
			break
		}
		page++
	}
	log.debug 'AmazonWebServices number of slide shares = ' + shows.size()
	return shows
}

String getYouTubeUrl(String sessNum, String queryString, YouTubeQuery query, YouTubeService service, DateTime newerThanDate) {
	query.setFullTextQuery(queryString)
	VideoFeed videoFeed = service.query(query, VideoFeed.class)
	
	def youtubeUrl = null
	for (int ii = 0; ii < videoFeed.entries.size(); ii++) {
		VideoEntry video = videoFeed.entries[ii]
		// TODO:  Ensure that common attributes of publisher, etc are correct
		String videoTitle = video.title.plainText
		if (!videoTitle.contains(sessNum)) {
			continue
		}
		videoId = video.id.substring(video.id.lastIndexOf(':') + 1)
		DateTime videoDateTime = new DateTime(video.published.value)
		if (videoDateTime.isAfter(newerThanDate)) {
			youtubeUrl = 'http://www.youtube.com/watch?v=' + videoId
			break
		}
	}
	youtubeUrl
}

SessionInfo[] getSessionInfos(Logger log, String filename, Slideshow[] slideShares, ConfigObject config, DateTime newerThanDate) {
	def infos = []
	XmlSlurper slurp = new XmlSlurper(new Parser())
	
	GPathResult nodes = slurp.parse(filename)
	GPathResult sessions = nodes.children().children().findAll( { it.@id.text().contains('session_') } )
	
	YouTubeService service = new YouTubeService(config.converter.youtube.client.id)
	YouTubeQuery query = new YouTubeQuery(new URL("http://gdata.youtube.com/feeds/api/videos"))
	query.setOrderBy(YouTubeQuery.OrderBy.RELEVANCE)
	query.setSafeSearch(YouTubeQuery.SafeSearch.NONE)
	
	sessions.each { session ->
		def sessInfo = new SessionInfo()
		def snum = session.div.a.span.find { it.@class == 'abbreviation' }?.text().tokenize()[0]
		if (snum.endsWith('-R')) return true
		def titl = convertndashes(session.div.a.span.find { it.@class == 'title' }?.text())
		def abst = convertndashes(session.div.span.find { it.@class == 'abstract' }?.text())
		def spkr = convertndashes(session.div.small.find { it.@class == 'speakers' }?.text())
		log.debug 'session : ' + snum
		sessInfo.session = snum
		log.debug 'title: ' + titl
		sessInfo.title = titl
		log.debug 'speaker(s): ' + spkr
		sessInfo.speakers = spkr
		log.debug 'abstract: ' + abst
		sessInfo.abstract1 = abst
		
		String queryString = snum + ' re:Invent 2013'
		String youtubeUrl = getYouTubeUrl(snum, queryString, query, service, newerThanDate)
		if (!youtubeUrl) {
			// repeat session instead
			queryString = snum + 'R re:Invent 2013'
			youtubeUrl = getYouTubeUrl(snum, queryString, query, service, newerThanDate)
		}
		
		log.debug 'youtube url: ' + youtubeUrl
		sessInfo.youtubeUrl = youtubeUrl
		
		def slideshareUrl = null
		def allShows = slideShares.findAll({ it.title.contains(snum) && it.createdDate.isAfter(newerThanDate) })
		for (int ii = 0; ii < allShows.size(); ii++) {
			Slideshow show = allShows[ii]
			slideshareUrl = show.permalink
			break
		}
		log.debug 'slideshow url: ' + slideshareUrl
		sessInfo.slideshareUrl = slideshareUrl
		infos.add(sessInfo)
	}
	
	return infos
}

DateTime newerThanDate = new DateTime().minusDays(config.converter.daysAgo)
shows = getAmazonSlides(log, config)
def allInfos = new SessionInfoList()
config.converter.files.each { file ->
	filename = 'src/main/resources/' + file + '.html'
	def infos = getSessionInfos(log, filename, shows, config, newerThanDate)
	log.debug 'infos = ' + infos
	allInfos.infos.addAll(infos)
}
log.debug 'allInfos = ' + allInfos.infos

MustacheFactory mf = new DefaultMustacheFactory()
Mustache mustache = mf.compile('src/main/resources/out.mustache')
mustache.execute(new PrintWriter(System.out), allInfos).flush()