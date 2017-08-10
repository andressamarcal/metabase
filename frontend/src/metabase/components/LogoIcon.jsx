import React, { Component } from "react";
import PropTypes from "prop-types";
import cx from "classnames";

import { removeAllChildren, parseDataUri } from "metabase/lib/dom";

import { connect } from "react-redux";
import { getLogoUrl } from "metabase/selectors/settings";

const mapStateToProps = state => ({
    url: getLogoUrl(state)
});

@connect(mapStateToProps)
export default class LogoIcon extends Component {
    state = {
        svg: null
    };

    static defaultProps = {
        height: 32
    };

    static propTypes = {
        size: PropTypes.number,
        width: PropTypes.number,
        height: PropTypes.number,
        dark: PropTypes.bool
    };

    componentDidMount() {
        if (this.props.url) {
            this.loadImage(this.props.url);
        }
    }

    componentWillReceiveProps(newProps) {
        if (newProps.url && newProps.url !== this.props.url) {
            this.loadImage(newProps.url);
        }
    }

    loadImage(url) {
        const parsed = parseDataUri(url);

        if (parsed) {
            if (parsed.mimeType === "image/svg+xml") {
                this._container.innerHTML = parsed.data;
                const svg = this._container.getElementsByTagName("svg")[0];
                if (svg) {
                    svg.setAttribute("fill", "currentcolor");
                    this.updateSize(svg);
                } else {
                    this.loadImageFallback();
                }
            } else {
                this.loadImageFallback();
            }
        } else {
            const xhr = new XMLHttpRequest();
            xhr.open("GET", url);
            xhr.onload = () => {
                if (xhr.status < 200 || xhr.status >= 300) {
                    return;
                }
                const svg = xhr.responseXML && xhr.responseXML.getElementsByTagName("svg")[0];
                if (svg) {
                    svg.setAttribute("fill", "currentcolor");
                    this.updateSize(svg);

                    removeAllChildren(this._container);
                    this._container.appendChild(svg);
                } else {
                    this.loadImageFallback();
                }
            };
            xhr.onerror = () => {
                this.loadImageFallback();
            }
            xhr.send();
        }
    }

    loadImageFallback() {
        const img = document.createElement("img");
        img.src = this.props.url;
        this.updateSize(img);

        removeAllChildren(this._container);
        this._container.appendChild(img);
    }

    updateSize(element) {
        const width = this.props.width || this.props.size;
        const height = this.props.height || this.props.size;
        if (width) {
            element.setAttribute("width", width);
        } else {
            element.removeAttribute("width");
        }
        if (height) {
            element.setAttribute("height", height);
        } else {
            element.removeAttribute("height");
        }
    }

    render() {
        const { dark } = this.props;
        return (
            <span
                ref={c => this._container = c}
                className={cx({ "text-brand": !dark }, { "text-white": dark })}
            />
        );
    }
}
