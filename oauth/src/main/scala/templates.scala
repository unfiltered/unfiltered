package unfiltered.oauth

import unfiltered.response.Html

trait OAuthTemplates {
  def layout(body: xml.NodeSeq) = Html(
    <html>
      <head>
        <title>oauth template</title>
      </head>
      <body>
       {body}
      </body>
    </html>
  )
}