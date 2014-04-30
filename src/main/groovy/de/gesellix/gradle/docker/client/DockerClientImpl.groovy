package de.gesellix.gradle.docker.client

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseDecorator
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.runtime.MethodClosure
import org.mortbay.util.ajax.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DockerClientImpl implements DockerClient {

  private static Logger logger = LoggerFactory.getLogger(DockerClientImpl)

  def hostname
  def port
  def HTTPBuilder client

  DockerClientImpl(hostname = "172.17.42.1", port = 4243) {
    this.hostname = hostname
    this.port = port

    def dockerUri = "http://$hostname:$port/"
    this.client = new HTTPBuilder(dockerUri)
    logger.info "using docker at '${dockerUri}'"
  }

  @Override
  def build(InputStream buildContext) {
    logger.info "build image..."
    def responseHandler = new ChunkedResponseHandler()
    client.handler.'200' = new MethodClosure(responseHandler, "handleResponse")
    client.post([path              : "/build",
                 body: IOUtils.toByteArray(buildContext),
                 requestContentType: ContentType.BINARY])

    def lastResponseDetail = responseHandler.lastResponseDetail
    logger.info "${lastResponseDetail}"
    return lastResponseDetail.stream.trim()
  }

  @Override
  def tag(imageId, repositoryName) {
    logger.info "tag image"
    client.post([path : "/images/${imageId}/tag".toString(),
                 query: [repo : repositoryName,
                         force: 0]]) { response ->
      logger.info "${response.statusLine}"
      return response.statusLine.statusCode
    }
  }

  @Override
  def push() {
    logger.info "push image"
  }

  @Override
  def pull(imageName) {
    logger.info "pull image '${imageName}'..."

    def responseHandler = new ChunkedResponseHandler()
    client.handler.'200' = new MethodClosure(responseHandler, "handleResponse")
    client.post([path : "/images/create",
                 query: [fromImage: imageName]])

    def lastResponseDetail = responseHandler.lastResponseDetail
    logger.info "${lastResponseDetail}"
    return lastResponseDetail.id
  }

  @Override
  def createContainer(fromImage, cmd) {
    logger.info "create container..."
    client.post([path              : "/containers/create".toString(),
                 body              : ["Hostname"      : "",
                                      "User"          : "",
                                      "Memory"        : 0,
                                      "MemorySwap"    : 0,
                                      "AttachStdin"   : false,
                                      "AttachStdout"  : true,
                                      "AttachStderr"  : true,
                                      "PortSpecs"     : null,
                                      "Tty"           : false,
                                      "OpenStdin"     : false,
                                      "StdinOnce"     : false,
                                      "Env"           : null,
                                      "Cmd": cmd,
                                      "Image"         : fromImage,
                                      "Volumes"       : [],
                                      "WorkingDir"    : "",
                                      "DisableNetwork": false,
                                      "ExposedPorts"  : [
                                          "DisableNetwork": false
                                      ]
                 ],
                 requestContentType: ContentType.JSON]) { response, reader ->
      logger.info "${response.statusLine}"
      return reader
    }
  }

  @Override
  def startContainer(containerId) {
    logger.info "start container..."
    client.post([path              : "/containers/${containerId}/start".toString(),
                 body              : [:],
                 requestContentType: ContentType.JSON]) { response, reader ->
      logger.info "${response.statusLine}"
      return response.statusLine.statusCode
    }
  }

  @Override
  def stop() {
    logger.info "stop container"
  }

  @Override
  def rm() {
    logger.info "rm container"
  }

  @Override
  def rmi() {
    logger.info "rm image"
  }

  @Override
  def run() {
    logger.info "run container"
/*
    http://docs.docker.io/reference/api/docker_remote_api_v1.10/#3-going-further

    Here are the steps of ‘docker run’ :
      Create the container
      If the status code is 404, it means the image doesn’t exists: : - Try to pull it - Then retry to create the container
      Start the container
      If you are not in detached mode: : - Attach to the container, using logs=1 (to have stdout and stderr from the container’s start) and stream=1
      If in detached mode or only stdin is attached: : - Display the container’s id
*/
  }

  @Override
  def ps() {
    logger.info "list containers"
  }

  @Override
  def images() {
    logger.info "list images"
    client.get([path : "/images/json",
                query: [all: 0]]) { response, reader ->
      logger.info "${response.statusLine}"
      return reader
    }
  }

  static class ChunkedResponseHandler {

    def completeResponse = ""

    def handleResponse(HttpResponseDecorator response) {
      new InputStreamReader(response.entity.content).each { chunk ->
        logger.debug("received chunk: '${chunk}'")
        completeResponse += chunk
      }
    }

    def getLastResponseDetail() {
      logger.debug("find last detail in: '${completeResponse}'")
      def lastResponseDetail = completeResponse.substring(completeResponse.lastIndexOf("}{") + 1)
      return JSON.parse(lastResponseDetail)
    }
  }
}
