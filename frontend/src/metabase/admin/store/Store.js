import React from "react";
import { Box, Flex } from "grid-styled";
import { t } from "c-3po";
import { connect } from "react-redux";
import { push } from "react-router-redux";

import colors from "metabase/lib/colors";

import Button from "metabase/components/Button";
import Icon from "metabase/components/Icon";
import Link from "metabase/components/Link";
import Card from "metabase/components/Card";
import Text from "metabase/components/Text";

import ModalWithTrigger from "metabase/components/ModalWithTrigger";

import fitViewport from "metabase/hoc/FitViewPort";

import { SettingsApi } from "metabase/services";

@fitViewport
@connect(
  state => {
    const featuresEnabled = state.settings.values["premium_features"];
    const isActivated = Object.values(featuresEnabled).some(f => f === true);
    return {
      isActivated,
    };
  },
  { push },
)
class StoreApp extends React.Component {
  componentWillMount() {
    if (this.props.isActivated) {
      this.props.push("/admin/store/account");
    }
  }
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        className={this.props.fitClassNames}
      >
        <Box mr={4}>
          <StoreDetails />
        </Box>
        <Card>
          <Box w={400} p={3}>
            <h4>{t`Already have a key?`}</h4>
            <Link to="/admin/store/activate">
              <Button primary>{t`Activate a product`}</Button>
            </Link>
          </Box>
        </Card>
      </Flex>
    );
  }
}

const FEATURES = {
  sandboxes: {
    name: t`Data sandboxes`,
    description: t`Make sure you're showing the right people the right data with automatic and secure filters based on user attributes.`,
    icon: "lock",
    docs:
      "http://www.metabase.com/docs/latest/administration-guide/17-data-sandboxes.md",
  },
  whitelabel: {
    name: t`Whitelabeling`,
    description: t`Match Metabase to your brand with custom colors, your own logo and more.`,
    icon: "star",
    docs:
      "http://metabase.com/docs/latest/administration-guide/15-whitelabeling.md",
  },
  sso: {
    name: t`SSO`,
    description: t`Provide easy login that works with your exisiting authentication infrastructure.`,
    icon: "group",
    docsRender: () => (
      <Flex align="center">
        <a
          href="http://metabase.com/docs/latest/administration-guide/16-authenticating-with-saml.md"
          className="mx2 link"
        >
          SAML
        </a>
        <a
          href="http://metabase.com/docs/latest/administration-guide/18-authenticating-with-jwt.md"
          className="mx2 link"
        >
          JWT
        </a>
      </Flex>
    ),
  },
};

const ICON_SIZE = 22;
const WRAPPER_SIZE = ICON_SIZE * 2.5;

const IconWrapper = ({ children, color }) => (
  <Flex
    align="center"
    justify="center"
    p={2}
    bg={color || colors["brand"]}
    color="white"
    w={WRAPPER_SIZE}
    style={{ borderRadius: 99, height: WRAPPER_SIZE }}
  >
    {children}
  </Flex>
);

class StoreDetails extends React.Component {
  render() {
    return (
      <Box>
        <Box my={3}>
          <h2>{t`Enterprise features`}</h2>
        </Box>
        <Box is="ul" className="text-measure">
          {Object.values(FEATURES).map(({ name, description, icon }) => (
            <Flex>
              <IconWrapper>
                <Icon name={icon} size={ICON_SIZE} />
              </IconWrapper>
              <Box ml={1}>
                <h2>{name}</h2>
                <Text>{description}</Text>
              </Box>
            </Flex>
          ))}
        </Box>
        <a
          href="https://metabase.com/offerings/"
          target="_blank"
          className="my2"
        >
          <Button>{t`Learn more`}</Button>
        </a>
      </Box>
    );
  }
}

@fitViewport
@connect(state => {
  const featuresEnabled = state.settings.values["premium_features"];
  return {
    featuresEnabled,
  };
})
export class Account extends React.Component {
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        flexDirection="column"
        className={this.props.fitClassNames}
      >
        <Flex align="center" flexDirection="column" py={3}>
          <Box mb={3}>
            <h2>{t`Your features are active!`}</h2>
          </Box>
          <Flex align="center">
            {this.props.featuresEnabled &&
              Object.keys(this.props.featuresEnabled).map(f => {
                const feature = FEATURES[f];
                return (
                  feature && (
                    <Card p={4} mx={3}>
                      <Flex
                        align="center"
                        justify="center"
                        flexDirection="column"
                      >
                        <IconWrapper color={colors["success"]}>
                          <Icon name={feature.icon} size={ICON_SIZE} />
                        </IconWrapper>
                        <Box my={2}>
                          <h3>{feature.name}</h3>
                        </Box>
                        {feature.docs && (
                          <a href={feature.docs} className="link">
                            {t`Learn how to use this`}
                          </a>
                        )}
                        {feature.docsRender && feature.docsRender()}
                      </Flex>
                    </Card>
                  )
                );
              })}
          </Flex>
        </Flex>
      </Flex>
    );
  }
}

@fitViewport
export class Activate extends React.Component {
  state = {
    heading: t`Enter the token you recieved from the store`,
    errorMessage: "",
    showVerbose: false,
    error: false,
  };
  activate = async () => {
    const value = this._input.value.trim();
    if (!value) {
      return false;
    }
    try {
      await SettingsApi.put({ key: "premium-embedding-token", value });
      // set window.location so we do a hard refresh
      window.location = "/admin/store/account";
    } catch (e) {
      console.error(e.data);
      this.setState({
        error: true,
        heading: e.data.message,
        errorMessage: e.data["error-details"],
      });
    }
  };
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        className={this.props.fitClassNames}
      >
        <Flex align="center" flexDirection="column">
          <Box my={3}>
            <h2
              className="text-centered"
              style={{ color: this.state.error ? colors["error"] : "inherit" }}
            >
              {this.state.heading}
            </h2>
          </Box>
          <Box>
            <input
              ref={ref => (this._input = ref)}
              type="text"
              className="input"
              placeholder="XXXX-XXXX-XXXX-XXXX"
            />
            <Button onClick={this.activate}>{t`Activate`}</Button>
          </Box>

          {this.state.error && (
            <ModalWithTrigger
              triggerElement={
                <Box mt={3}>
                  <Link
                    className="link"
                    onClick={() => this.setState({ showVerbose: true })}
                  >{t`Need help?`}</Link>
                </Box>
              }
              onClose={() => this.setState({ showVerbose: false })}
              title={t`More info about your problem.`}
              open={this.state.showVerbose}
            >
              <Box>{this.state.errorMessage}</Box>
              <Flex my={2}>
                <a
                  className="ml-auto"
                  href={`mailto:support@metabase.com?Subject="Issue with token activation for token ${
                    this._input.value
                  }"&Body="${this.state.errorMessage}"`}
                >
                  <Button primary>{t`Contact support`}</Button>
                </a>
              </Flex>
            </ModalWithTrigger>
          )}
        </Flex>
      </Flex>
    );
  }
}

export default StoreApp;
