exports.handler = async (event) => {
    return {
        statusCode: 200,
        message: "Hello from Lambda" // Placing message outside body
    };
};
