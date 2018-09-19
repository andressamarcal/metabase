import React from "react";
import { Box, Flex } from "grid-styled";
import { t } from "c-3po";

import colors from "metabase/lib/colors";

import Button from "metabase/components/Button";
import Icon from "metabase/components/Icon";
import Link from "metabase/components/Link";
import Card from "metabase/components/Card";
import Text from "metabase/components/Text";

import fitViewport from "metabase/hoc/FitViewPort";

@fitViewport
class StoreApp extends React.Component {
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        className={this.props.fitClassNames}
      >
        <Box>
          <StoreDetails />
        </Box>
        <Card p={3}>
          <h4>{t`Already have a key?`}</h4>
          <Link to="/admin/store/activate">
            <Button>{t`Activate a product`}</Button>
          </Link>
        </Card>
      </Flex>
    );
  }
}

class StoreDetails extends React.Component {
  render() {
    return (
      <Box>
        <h2>{t`Enterprise features`}</h2>
        <Box is="ul" className="text-measure">
          <Flex>
            <Icon name="lock" size={28} />
            <Box>
              <h2>{t`Row level permissions`}</h2>
              <Text>
                {t`Make sure you're showing the right people the right data with automatic and secure filters based on user attributes.`}
              </Text>
            </Box>
          </Flex>
          <Flex>
            <Icon name="star" size={28} />
            <Box>
              <h2>{t`Whitelabeling`}</h2>
              <Text>
                {t`Match Metabase to your brand with custom colors, your own logo and more.`}
              </Text>
            </Box>
          </Flex>
          <Flex>
            <Icon name="star" size={28} />
            <Box>
              <h2>{t`SSO`}</h2>
              <Text>
                {t`Provide easy login that works with your exisiting authentication infrastructure.`}
              </Text>
            </Box>
          </Flex>
        </Box>
        <Link to="">
          <Button primary>{t`Learn more`}</Button>
        </Link>
      </Box>
    );
  }
}

@fitViewport
export class Account extends React.Component {
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        flexDirection="column"
        className={this.props.fitClassNames}
      >
        <Box my={3}>
          <Icon name="check" size={64} color={colors["success"]} />
        </Box>
        <Box py={3}>
          <h2>{t`Your features are active!`}</h2>
        </Box>
      </Flex>
    );
  }
}

@fitViewport
export class Activate extends React.Component {
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        className={this.props.fitClassNames}
      >
        <Box>
          <Box my={3}>
            <h2 className="text-centered">
              {t`Enter the token you recieved from the store`}
            </h2>
          </Box>
          <input
            type="text"
            className="input"
            placeholder="XXXX-XXXX-XXXX-XXXX"
          />
          <Link to="/admin/store/account">
            <Button>{t`Activate`}</Button>
          </Link>
        </Box>
      </Flex>
    );
  }
}

export default StoreApp;
